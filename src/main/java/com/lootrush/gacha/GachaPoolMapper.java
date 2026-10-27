package com.lootrush.gacha;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GachaPoolMapper {

    @Select("SELECT * FROM gacha_pool ORDER BY id")
    List<GachaPoolEntity> selectAll();

    @Select("SELECT name FROM item WHERE id = #{itemId}")
    String itemNameOf(@Param("itemId") Long itemId);
}
