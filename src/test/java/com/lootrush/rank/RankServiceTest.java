package com.lootrush.rank;

import com.lootrush.gacha.GachaService;
import com.lootrush.user.AuthService;
import com.lootrush.user.UserEntity;
import com.lootrush.user.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 循环9 测试：排行榜计分、查询与 30 万级造数（小规模）。
 *
 * 刻意不加 @Transactional：计分挂在 afterCommit 上（事务回滚测试里永远不触发），
 * 必须走真实提交路径——与 ReconServiceTest 同一取舍。副作用靠 @AfterEach 清理。
 */
@SpringBootTest
class RankServiceTest {

    @Autowired RankService rankService;
    @Autowired GachaService gachaService;
    @Autowired AuthService authService;
    @Autowired UserMapper userMapper;
    @Autowired StringRedisTemplate redis;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Long userId;

    @AfterEach
    void clean() {
        if (userId != null) {
            redis.opsForZSet().remove(RankService.KEY, String.valueOf(userId));
        }
        // 造数玩家清理
        for (long id = RankService.BOT_ID_BASE + 1; id <= RankService.BOT_ID_BASE + 500; id++) {
            redis.opsForZSet().remove(RankService.KEY, String.valueOf(id));
        }
        // 非事务测试会真实提交数据：用户删除后，按孤儿行清理其各表残留
        if (userId != null) {
            userMapper.deleteByUserId(userId);
            jdbc.update("DELETE FROM gacha_record WHERE user_id NOT IN (SELECT user_id FROM user)");
            jdbc.update("DELETE FROM wallet_flow WHERE user_id NOT IN (SELECT user_id FROM user)");
            jdbc.update("DELETE FROM bag WHERE user_id NOT IN (SELECT user_id FROM user)");
            jdbc.update("DELETE FROM user_pity WHERE user_id NOT IN (SELECT user_id FROM user)");
        }
    }

    @Test
    void draw_consumption_counts_into_rank_after_commit() {
        UserEntity u = authService.register("ranker" + System.currentTimeMillis() % 1000000, "pass123");
        userId = u.getId();
        userMapper.addDiamond(userId, 5000);

        gachaService.draw(userId, 10); // 消费1000（事务真实提交→afterCommit触发计分）

        RankService.MeRank me = rankService.me(userId);
        assertNotNull(me.rank(), "真实消费后必须上榜");
        assertEquals(1000, me.score(), "榜内分数应等于消费额");
        assertTrue(me.totalMembers() >= 1);
    }

    @Test
    void top_orders_by_score_desc_and_enriches_names() {
        UserEntity a = authService.register("topa" + System.currentTimeMillis() % 1000000, "pass123");
        UserEntity b = authService.register("topb" + System.currentTimeMillis() % 1000000, "pass123");
        long botId = RankService.BOT_ID_BASE + 499; // 落在 @AfterEach 清理区间
        rankService.addConsumptionNow(a.getId(), 300);
        rankService.addConsumptionNow(b.getId(), 700);
        rankService.addConsumptionNow(botId, 500);

        var top = rankService.top(10);
        assertEquals(700, top.get(0).score(), "最高分应排第一");
        assertEquals(b.getId(), top.get(0).userId(), "第一名应是700分的玩家");
        // 全榜降序
        for (int i = 1; i < top.size(); i++) {
            assertTrue(top.get(i - 1).score() >= top.get(i).score(), "必须降序");
        }
        // 真实玩家名字补全、机器人走兜底名
        assertTrue(top.stream().anyMatch(e -> e.name().startsWith("topb")));
        assertTrue(top.stream().anyMatch(e -> e.name().startsWith("神秘玩家")));

        // 清理
        redis.opsForZSet().remove(RankService.KEY, a.getId().toString(), b.getId().toString(), String.valueOf(botId));
    }

    @Test
    void me_rank_among_seeded_bots_is_correct() {
        UserEntity u = authService.register("meself" + System.currentTimeMillis() % 1000000, "pass123");
        userId = u.getId();
        // 造 100 个机器人（分数 50~50000），我消费 25000 —— 排名应在机器人高分段之后
        for (long id = RankService.BOT_ID_BASE + 1; id <= RankService.BOT_ID_BASE + 100; id++) {
            rankService.addConsumptionNow(id, 50);
        }
        rankService.addConsumptionNow(userId, 25000);

        RankService.MeRank me = rankService.me(userId);
        assertEquals(25000, me.score());
        assertTrue(me.rank() >= 1 && me.rank() <= 10,
                "25000分在100个50~50000分的机器人中应进前十（大概率前几名），实际rank=" + me.rank());
    }
}
