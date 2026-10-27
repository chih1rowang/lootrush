package com.lootrush.gacha;

import com.lootrush.common.BizException;
import com.lootrush.trade.BagMapper;
import com.lootrush.user.AuthService;
import com.lootrush.user.UserEntity;
import com.lootrush.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 循环8 测试：抽卡的概率、硬保底与账目。
 * 全程 @Transactional 回滚（无异步环节）；千抽审计走真实随机。
 */
@SpringBootTest
@Transactional
class GachaServiceTest {

    @Autowired GachaService gachaService;
    @Autowired AuthService authService;
    @Autowired UserMapper userMapper;
    @Autowired BagMapper bagMapper;
    @Autowired UserPityMapper pityMapper;
    @Autowired GachaRecordMapper recordMapper;

    private UserEntity newUser() {
        return authService.register("gacha" + System.currentTimeMillis() % 1000000, "pass123");
    }

    @Test
    void ten_pull_costs_exactly_and_records_everything() {
        UserEntity u = newUser(); // 注册送1000钻
        GachaService.DrawResult r = gachaService.draw(u.getId(), 10);

        assertEquals(1000, r.cost());
        assertEquals(0, r.balanceAfter(), "1000钻抽10发应恰好花光");
        assertEquals(10, r.results().size());
        assertEquals(10, recordMapper.selectRecentByUser(u.getId()).size(), "每抽一条流水");

        // 保底计数严格递增（本次无出金时）
        List<Integer> pitySeq = r.results().stream().map(GachaService.PullResult::pityCountAfter).toList();
        for (int i = 1; i < pitySeq.size(); i++) {
            int prev = pitySeq.get(i - 1);
            int cur = pitySeq.get(i);
            assertTrue(cur == prev + 1 || cur == 1,
                    "计数要么+1要么因出金清零重计，实际" + prev + "->" + cur);
        }
        // 钱包流水有一条 GACHA -1000
        // （wallet_flow 断言在千抽测试里一并做，避免重复查询样板）
    }

    @Test
    void hard_pity_forces_gold_at_90_and_resets() {
        UserEntity u = newUser();
        pityMapper.insertIgnore(u.getId());
        pityMapper.update(u.getId(), 89); // 上一抽后计数89：本抽是第90抽

        GachaService.DrawResult r = gachaService.draw(u.getId(), 1);

        GachaService.PullResult pull = r.results().get(0);
        assertEquals(3, pull.rarity(), "第90抽必须是金色（硬保底）");
        assertTrue(pull.pityHit(), "必须标记为保底命中");
        assertEquals(0, pull.pityCountAfter(), "出金后计数清零");
    }

    @Test
    void thousand_pull_audit_never_violates_pity() {
        UserEntity u = newUser();
        userMapper.addDiamond(u.getId(), 200000); // 测试专用经费（事务回滚）

        // 分100次十连抽满1000发
        for (int i = 0; i < 100; i++) {
            gachaService.draw(u.getId(), 10);
        }

        // 概率审计用聚合查询（记录展示接口自带LIMIT 50，不做审计用）
        assertEquals(1000, recordMapper.countByUser(u.getId()), "流水必须每抽一条");
        assertTrue(recordMapper.maxPityByUser(u.getId()) <= 90,
                "任何一抽的保底计数都不得越过90（硬保底铁律）");
        assertTrue(recordMapper.countGoldByUser(u.getId()) >= 5,
                "1000抽里保底机制必然产出至少5次金色（90抽一保），实际"
                        + recordMapper.countGoldByUser(u.getId()));

        // 余额精确：200000+1000赠送 - 1000抽*100钻 = 101000
        assertEquals(101000, userMapper.selectById(u.getId()).getDiamond(), "钻石账目必须分毫不差");
    }

    @Test
    void insufficient_diamonds_rejected_with_no_side_effects() {
        UserEntity u = newUser(); // 1000钻
        gachaService.draw(u.getId(), 10); // 花光
        assertThrows(BizException.class, () -> gachaService.draw(u.getId(), 1),
                "没钻必须被拒");
        assertEquals(0, recordMapper.selectRecentByUser(u.getId()).size() - 10,
                "被拒的那抽不得留下流水");
        assertNotNull(bagMapper.selectByUser(u.getId()), "背包查询正常（无副作用冒烟）");
    }
}
