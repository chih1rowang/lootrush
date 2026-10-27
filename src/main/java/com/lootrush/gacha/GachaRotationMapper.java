package com.lootrush.gacha;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface GachaRotationMapper {

    @Select("SELECT * FROM gacha_gold_item ORDER BY slot_order")
    List<GachaGoldItemEntity> selectGoldQueue();

    @Select("SELECT i.name FROM gacha_gold_item g JOIN item i ON g.item_id = i.id WHERE g.item_id = #{itemId}")
    String itemNameOf(@Param("itemId") Long itemId);

    @Select("SELECT anchor_start FROM gacha_rotation_state WHERE id = 1")
    java.time.LocalDateTime selectAnchor();

    @Update("UPDATE gacha_rotation_state SET anchor_start = #{anchor} WHERE id = 1")
    int updateAnchor(@Param("anchor") java.time.LocalDateTime anchor);
}
