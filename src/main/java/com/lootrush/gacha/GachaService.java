package com.lootrush.gacha;

import com.lootrush.common.BizException;
import com.lootrush.rank.RankService;
import com.lootrush.trade.BagMapper;
import com.lootrush.user.UserMapper;
import com.lootrush.user.WalletFlowEntity;
import com.lootrush.user.WalletFlowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 抽卡服务（技术总表场景12）：概率 + 硬保底90 + 不可篡改流水。
 *
 * 概率结构（循环9D起）：
 * - 基础卡池（不变）：蓝 890/1000 = 89%，紫 100/1000 = 10%；
 * - 金色共 10/1000 = 1%，**不固定某一件**——10 件金装按 5 件一组每周轮换
 *   （组序号 = 距纪元起点的整周数 % 组数，惰性计算，无需定时任务）；
 *   每期内 5 件均分这 1%（各 0.2%）。
 * - 硬保底 90：计数到 90 强制从本期金装里出一件并清零计数；自然出金同样清零。
 *
 * 四动作一个事务（扣钻、出卡、保底计数、流水）；同玩家并发抽卡在
 * user_pity 行锁上排队。
 */
@Service
public class GachaService {

    public static final int PRICE_PER_PULL = 160;
    public static final int PITY_THRESHOLD = 90;
    public static final int RARITY_GOLD = 3;
    public static final int BASE_TOTAL_WEIGHT = 1000;
    public static final int GOLD_WEIGHT = 10;
    public static final int GROUP_SIZE = 5;

    private static final Logger log = LoggerFactory.getLogger(GachaService.class);

    private final GachaPoolMapper poolMapper;
    private final GachaRecordMapper recordMapper;
    private final UserPityMapper pityMapper;
    private final GachaRotationMapper rotationMapper;
    private final UserMapper userMapper;
    private final WalletFlowMapper walletFlowMapper;
    private final BagMapper bagMapper;
    private final RankService rankService;

    public GachaService(GachaPoolMapper poolMapper, GachaRecordMapper recordMapper,
                        UserPityMapper pityMapper, GachaRotationMapper rotationMapper,
                        UserMapper userMapper, WalletFlowMapper walletFlowMapper,
                        BagMapper bagMapper, RankService rankService) {
        this.poolMapper = poolMapper;
        this.recordMapper = recordMapper;
        this.pityMapper = pityMapper;
        this.rotationMapper = rotationMapper;
        this.userMapper = userMapper;
        this.walletFlowMapper = walletFlowMapper;
        this.bagMapper = bagMapper;
        this.rankService = rankService;
    }

    /** 单抽结果 */
    public record PullResult(Long itemId, String itemName, int rarity,
                             boolean pityHit, int pityCountAfter) {
    }

    public record DrawResult(String batchNo, int cost, int balanceAfter, List<PullResult> results) {
    }

    /** 本期/下期金装阵容（前端卡池页展示"更新时间与下轮道具"的数据源） */
    public record GoldLineup(List<Long> currentItemIds, List<String> currentItemNames,
                             List<String> nextItemNames, LocalDateTime periodStart,
                             LocalDateTime periodEnd) {
    }

    @Transactional
    public DrawResult draw(Long userId, int count) {
        if (count != 1 && count != 10) {
            throw BizException.of(400, "次数只能为1或10");
        }
        int cost = PRICE_PER_PULL * count;
        if (userMapper.deductDiamond(userId, cost) == 0) {
            throw BizException.of(400, "钻石余额不足");
        }

        pityMapper.insertIgnore(userId);
        int pity = pityMapper.selectForUpdate(userId);

        List<GachaPoolEntity> basePool = poolMapper.selectAll();
        if (basePool.isEmpty()) {
            throw BizException.of(500, "卡池未配置");
        }
        List<Long> goldLineup = currentGoldLineup().currentItemIds();

        String batchNo = "G" + System.currentTimeMillis() + userId;
        List<PullResult> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            pity++;
            Long pickedItemId = pickItem(basePool, goldLineup);
            int rarity = pickedRarity(pickedItemId, basePool);
            boolean pityHit = false;
            if (rarity != RARITY_GOLD && pity >= PITY_THRESHOLD) {
                pickedItemId = goldLineup.get(ThreadLocalRandom.current().nextInt(goldLineup.size()));
                rarity = RARITY_GOLD; // 硬保底：第90抽强制本期金装
                pityHit = true;
            }
            if (rarity == RARITY_GOLD) {
                pity = 0; // 出金清零（自然出与强制出同规则）
            }
            saveRecord(userId, pickedItemId, rarity, pity, pityHit, batchNo);
            bagMapper.upsertAdd(userId, pickedItemId, 1);
            results.add(new PullResult(pickedItemId,
                    poolMapper.itemNameOf(pickedItemId), rarity, pityHit, pity));
        }
        pityMapper.update(userId, pity);

        writeFlow(userId, cost);
        rankService.recordAfterCommit(userId, cost);
        log.info("抽卡完成 userId={} count={} cost={} batchNo={}", userId, count, cost, batchNo);
        return new DrawResult(batchNo, cost, userMapper.selectById(userId).getDiamond(), results);
    }

    /** 概率公示 + 本期/下期金装 + 保底进度（前端抽卡页数据源） */
    public PoolVO poolOf(Long userId) {
        List<GachaPoolEntity> basePool = poolMapper.selectAll();
        pityMapper.insertIgnore(userId);
        Integer pity = pityMapper.selectForUpdate(userId);

        GoldLineup lineup = currentGoldLineup();
        double goldShare = (double) GOLD_WEIGHT / BASE_TOTAL_WEIGHT / lineup.currentItemIds().size();

        List<PoolItem> items = new ArrayList<>();
        for (GachaPoolEntity p : basePool) {
            items.add(new PoolItem(p.getItemId(), poolMapper.itemNameOf(p.getItemId()),
                    p.getRarity(), p.getWeight(),
                    String.format("%.2f%%", p.getWeight() * 100.0 / BASE_TOTAL_WEIGHT)));
        }
        for (int i = 0; i < lineup.currentItemIds().size(); i++) {
            Long id = lineup.currentItemIds().get(i);
            items.add(new PoolItem(id, lineup.currentItemNames().get(i), RARITY_GOLD,
                    GOLD_WEIGHT / lineup.currentItemIds().size(),
                    String.format("%.2f%%", goldShare * 100)));
        }
        return new PoolVO(PRICE_PER_PULL, PITY_THRESHOLD, pity == null ? 0 : pity,
                lineup.periodStart(), lineup.periodEnd(), lineup.nextItemNames(), items);
    }

    public record PoolItem(Long itemId, String name, int rarity, int weight, String probability) {
    }

    public record PoolVO(int pricePerPull, int pityThreshold, int myPityCount,
                         LocalDateTime goldPeriodStart, LocalDateTime goldPeriodEnd,
                         List<String> nextGoldItemNames, List<PoolItem> items) {
    }

    public List<GachaRecordFlat> recordsOf(Long userId) {
        return recordMapper.selectRecentByUser(userId);
    }

    /** 当前金装阵容（惰性轮换：组序号=距纪元起点的整周数%组数，无需定时任务） */
    public GoldLineup currentGoldLineup() {
        List<GachaGoldItemEntity> queue = rotationMapper.selectGoldQueue();
        if (queue.isEmpty()) {
            throw BizException.of(500, "金装队列未配置");
        }
        LocalDateTime anchor = rotationMapper.selectAnchor();
        int groups = (queue.size() + GROUP_SIZE - 1) / GROUP_SIZE;
        long periods = Duration.between(anchor, LocalDateTime.now()).toDays() / 7;
        int idx = (int) (periods % groups);

        List<Long> currentIds = new ArrayList<>();
        List<String> currentNames = new ArrayList<>();
        int from = idx * GROUP_SIZE;
        int to = Math.min(from + GROUP_SIZE, queue.size());
        for (int i = from; i < to; i++) {
            currentIds.add(queue.get(i).getItemId());
            currentNames.add(poolMapper.itemNameOf(queue.get(i).getItemId()));
        }
        int nextIdx = (idx + 1) % groups;
        int nFrom = nextIdx * GROUP_SIZE;
        int nTo = Math.min(nFrom + GROUP_SIZE, queue.size());
        List<String> nextNames = new ArrayList<>();
        for (int i = nFrom; i < nTo; i++) {
            nextNames.add(poolMapper.itemNameOf(queue.get(i).getItemId()));
        }
        LocalDateTime start = anchor.plusDays((long) idx * 7);
        return new GoldLineup(currentIds, currentNames, nextNames, start, start.plusDays(7));
    }

    /** 基础卡池加权随机：蓝 890 / 紫 100 / 金 10（金色落到本期阵容随机一件） */
    private Long pickItem(List<GachaPoolEntity> basePool, List<Long> goldLineup) {
        int roll = ThreadLocalRandom.current().nextInt(BASE_TOTAL_WEIGHT);
        int acc = 0;
        for (GachaPoolEntity p : basePool) {
            acc += p.getWeight();
            if (roll < acc) {
                return p.getItemId();
            }
        }
        return goldLineup.get(ThreadLocalRandom.current().nextInt(goldLineup.size())); // 金色区间
    }

    private int pickedRarity(Long itemId, List<GachaPoolEntity> basePool) {
        return basePool.stream()
                .filter(p -> p.getItemId().equals(itemId))
                .findFirst()
                .map(GachaPoolEntity::getRarity)
                .orElse(RARITY_GOLD);
    }

    private void saveRecord(Long userId, Long itemId, int rarity, int pity, boolean pityHit, String batchNo) {
        GachaRecordEntity r = new GachaRecordEntity();
        r.setUserId(userId);
        r.setItemId(itemId);
        r.setRarity(rarity);
        r.setPityCountAfter(pity);
        r.setIsPityHit(pityHit ? 1 : 0);
        r.setBatchNo(batchNo);
        recordMapper.insert(r);
    }

    private void writeFlow(Long userId, int cost) {
        WalletFlowEntity flow = new WalletFlowEntity();
        flow.setUserId(userId);
        flow.setChangeType("GACHA");
        flow.setAmount(-cost);
        flow.setBalanceAfter(userMapper.selectById(userId).getDiamond());
        flow.setRefOrderNo("GACHA");
        walletFlowMapper.insert(flow);
    }
}
