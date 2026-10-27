package com.lootrush.gacha;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GachaRecordMapper {

    /** 只增不改：抽卡流水永不允许 UPDATE/DELETE */
    @Insert("INSERT INTO gacha_record(user_id, item_id, rarity, pity_count_after, is_pity_hit, batch_no) " +
            "VALUES(#{userId}, #{itemId}, #{rarity}, #{pityCountAfter}, #{isPityHit}, #{batchNo})")
    int insert(GachaRecordEntity record);

    /** 最近记录（联出道具名，供抽卡记录弹层展示） */
    @Select("SELECT r.id, r.user_id AS userId, r.item_id AS itemId, i.name AS itemName, " +
            "r.rarity, r.pity_count_after AS pityCountAfter, r.is_pity_hit AS isPityHit, " +
            "r.batch_no AS batchNo, r.create_time AS createTime " +
            "FROM gacha_record r JOIN item i ON r.item_id = i.id " +
            "WHERE r.user_id = #{userId} ORDER BY r.id DESC LIMIT 50")
    List<GachaRecordFlat> selectRecentByUser(@Param("userId") Long userId);

    /** 概率审计三件套：总抽数 / 出金数 / 历史最大保底计数 */
    @Select("SELECT COUNT(*) FROM gacha_record WHERE user_id = #{userId}")
    int countByUser(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM gacha_record WHERE user_id = #{userId} AND rarity = 3")
    int countGoldByUser(@Param("userId") Long userId);

    @Select("SELECT IFNULL(MAX(pity_count_after), 0) FROM gacha_record WHERE user_id = #{userId}")
    int maxPityByUser(@Param("userId") Long userId);
}
