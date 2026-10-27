package com.lootrush.user;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 用户表的增删查（MyBatis 注解方式：SQL 直接写在方法上） */
@Mapper
public interface UserMapper {

    @Insert("INSERT INTO user(username, password_hash, diamond) VALUES(#{username}, #{passwordHash}, #{diamond})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserEntity user);

    @Select("SELECT * FROM user WHERE username = #{username}")
    UserEntity selectByUsername(@Param("username") String username);

    @Select("SELECT * FROM user WHERE id = #{id}")
    UserEntity selectById(@Param("id") Long id);

    /** 条件扣减：余额不足时影响行数为0，绝不出现负数 */
    @Update("UPDATE user SET diamond = diamond - #{amount} WHERE id = #{id} AND diamond >= #{amount}")
    int deductDiamond(@Param("id") Long id, @Param("amount") int amount);

    @Update("UPDATE user SET diamond = diamond + #{amount} WHERE id = #{id}")
    int addDiamond(@Param("id") Long id, @Param("amount") int amount);

    /** 每个玩家的历史总消费（排行榜回填用）：PURCHASE/GACHA 的负流水取绝对值求和 */
    @Select("SELECT user_id AS userId, SUM(-amount) AS value FROM wallet_flow " +
            "WHERE change_type IN ('PURCHASE','GACHA') GROUP BY user_id")
    java.util.List<com.lootrush.rank.ConsumptionRow> selectConsumptionByUser();

    /** 批量取用户（排行榜前100补全名字用） */
    @Select("<script>SELECT * FROM user WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    java.util.List<UserEntity> selectByIds(@Param("ids") java.util.List<Long> ids);

    /** 测试清理用 */
    @org.apache.ibatis.annotations.Delete("DELETE FROM user WHERE id = #{id}")
    int deleteByUserId(@Param("id") Long id);
}
