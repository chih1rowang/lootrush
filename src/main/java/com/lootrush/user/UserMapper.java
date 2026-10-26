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
}
