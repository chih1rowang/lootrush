package com.lootrush.gacha;

import com.lootrush.common.BizException;
import com.lootrush.trade.BagMapper;
import com.lootrush.user.UserMapper;
import com.lootrush.user.WalletFlowEntity;
import com.lootrush.user.WalletFlowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 抽卡服务（技术总表场景12）：概率 + 硬保底90 + 不可篡改流水。
 *
 * 四动作一个事务（扣钻、出卡、保底计数、流水）——要么全成要么全回滚，
 * 绝不出现"扣了钻没出卡"或"出了卡计数没走"。
 *
 * 保底规则（硬保底90）：保底计数从 0 起每抽 +1；抽到金色（自然出或强制出）立即清零；
 * 计数到 90 时本抽强制改为金色并标记 is_pity_hit——玩家抽满 90 次必出金，公示可审计。
 *
 * 并发：同一玩家并发抽卡在 user_pity 行锁（SELECT FOR UPDATE）上排队，
 * 保底计数绝不跳变；不同玩家互不影响。
 */
@Service
public class GachaService {

    public static final int PRICE_PER_PULL = 100;
    public static final int PITY_THRESHOLD = 90;
    public static final int RARITY_GOLD = 3;

    private static final Logger log = LoggerFactory.getLogger(GachaService.class);

    private final GachaPoolMapper poolMapper;
    private final GachaRecordMapper recordMapper;
    private final UserPityMapper pityMapper;
    private final UserMapper userMapper;
    private final WalletFlowMapper walletFlowMapper;
    private final BagMapper bagMapper;

    public GachaService(GachaPoolMapper poolMapper, GachaRecordMapper recordMapper,
                        UserPityMapper pityMapper, UserMapper userMapper,
                        WalletFlowMapper walletFlowMapper, BagMapper bagMapper) {
        this.poolMapper = poolMapper;
        this.recordMapper = recordMapper;
        this.pityMapper = pityMapper;
        this.userMapper = userMapper;
        this.walletFlowMapper = walletFlowMapper;
        this.bagMapper = bagMapper;
    }

    /** 单抽结果 */
    public record PullResult(Long itemId, String itemName, int rarity,
                             boolean pityHit, int pityCountAfter) {
    }

    public record DrawResult(String batchNo, int cost, int balanceAfter, List<PullResult> results) {
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

        // 保底计数行锁：同玩家并发抽卡在此排队（首抽先建行）
        pityMapper.insertIgnore(userId);
        int pity = pityMapper.selectForUpdate(userId);

        List<GachaPoolEntity> pool = poolMapper.selectAll();
        if (pool.isEmpty()) {
            throw BizException.of(500, "卡池未配置");
        }
        int totalWeight = pool.stream().mapToInt(GachaPoolEntity::getWeight).sum();
        GachaPoolEntity goldItem = pool.stream()
                .filter(p -> p.getRarity() == RARITY_GOLD).findFirst()
                .orElseThrow(() -> BizException.of(500, "卡池缺少金色物品"));

        String batchNo = "G" + System.currentTimeMillis() + userId;
        List<PullResult> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            pity++;
            GachaPoolEntity picked = weightedPick(pool, totalWeight);
            boolean pityHit = false;
            if (picked.getRarity() != RARITY_GOLD && pity >= PITY_THRESHOLD) {
                picked = goldItem; // 硬保底：第90抽强制金色
                pityHit = true;
            }
            if (picked.getRarity() == RARITY_GOLD) {
                pity = 0; // 出金清零（自然出与强制出同规则）
            }
            saveRecord(userId, picked, pity, pityHit, batchNo);
            bagMapper.upsertAdd(userId, picked.getItemId(), 1);
            results.add(new PullResult(picked.getItemId(),
                    poolMapper.itemNameOf(picked.getItemId()),
                    picked.getRarity(), pityHit, pity));
        }
        pityMapper.update(userId, pity);

        writeFlow(userId, cost);
        log.info("抽卡完成 userId={} count={} cost={} batchNo={}", userId, count, cost, batchNo);
        return new DrawResult(batchNo, cost, userMapper.selectById(userId).getDiamond(), results);
    }

    /** 概率公示 + 当前保底进度（页面抽卡页的数据源） */
    public PoolVO poolOf(Long userId) {
        List<GachaPoolEntity> pool = poolMapper.selectAll();
        int total = pool.stream().mapToInt(GachaPoolEntity::getWeight).sum();
        pityMapper.insertIgnore(userId);
        Integer pity = pityMapper.selectForUpdate(userId);
        List<PoolItem> items = pool.stream().map(p -> new PoolItem(
                p.getItemId(), poolMapper.itemNameOf(p.getItemId()), p.getRarity(),
                p.getWeight(), String.format("%.2f%%", p.getWeight() * 100.0 / total))).toList();
        return new PoolVO(PRICE_PER_PULL, PITY_THRESHOLD, pity == null ? 0 : pity, items);
    }

    public record PoolItem(Long itemId, String name, int rarity, int weight, String probability) {
    }

    public record PoolVO(int pricePerPull, int pityThreshold, int myPityCount, List<PoolItem> items) {
    }

    public List<GachaRecordFlat> recordsOf(Long userId) {
        return recordMapper.selectRecentByUser(userId);
    }

    private GachaPoolEntity weightedPick(List<GachaPoolEntity> pool, int totalWeight) {
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int acc = 0;
        for (GachaPoolEntity p : pool) {
            acc += p.getWeight();
            if (roll < acc) {
                return p;
            }
        }
        return pool.get(pool.size() - 1); // 理论不可达，防御性兜底
    }

    private void saveRecord(Long userId, GachaPoolEntity picked, int pity, boolean pityHit, String batchNo) {
        GachaRecordEntity r = new GachaRecordEntity();
        r.setUserId(userId);
        r.setItemId(picked.getItemId());
        r.setRarity(picked.getRarity());
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
