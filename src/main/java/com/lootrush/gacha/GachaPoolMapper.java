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

    @Select("SELECT i.name FROM gacha_pool p JOIN item i ON p.item_id = i.id WHERE p.item_id = #{itemId}")
    String itemNameOf(@Param("itemId") Long itemId);
}
