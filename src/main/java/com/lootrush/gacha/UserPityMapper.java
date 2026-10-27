package com.lootrush.gacha;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 保底计数表的数据访问。
 * 选型说明（技术总表场景12）：保底计数是"丢失容忍度=0"的资金相关数据——
 * 玩家抽了 89 次服务器重启计数清零 = 舆情事故，因此落 MySQL（每笔落盘）而非 Redis；
 * 排行榜丢一秒无感才配用 Redis。先问丢失容忍度，再选存储。
 */
@Mapper
public interface UserPityMapper {

    @Insert("INSERT IGNORE INTO user_pity(user_id, pity_count) VALUES(#{userId}, 0)")
    int insertIgnore(@Param("userId") Long userId);

    /** 行锁：同一玩家并发抽卡在这里排队，保底计数绝不跳变 */
    @Select("SELECT pity_count FROM user_pity WHERE user_id = #{userId} FOR UPDATE")
    Integer selectForUpdate(@Param("userId") Long userId);

    @Update("UPDATE user_pity SET pity_count = #{pityCount} WHERE user_id = #{userId}")
    int update(@Param("userId") Long userId, @Param("pityCount") int pityCount);
}
