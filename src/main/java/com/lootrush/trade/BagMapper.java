package com.lootrush.trade;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BagMapper {

    /** 堆叠式入包：没有则插入，有则数量累加（数据库层 UPSERT，天然原子） */
    @Insert("INSERT INTO bag(user_id, item_id, count) VALUES(#{userId}, #{itemId}, #{count}) " +
            "ON DUPLICATE KEY UPDATE count = count + #{count}")
    int upsertAdd(@Param("userId") Long userId, @Param("itemId") Long itemId, @Param("count") int count);

    /** 背包展示：联出道具名和稀有度 */
    @Select("SELECT b.item_id AS itemId, i.name, i.rarity, i.image_url AS imageUrl, b.count " +
            "FROM bag b JOIN item i ON b.item_id = i.id " +
            "WHERE b.user_id = #{userId} ORDER BY b.update_time DESC")
    List<BagFlat> selectByUser(@Param("userId") Long userId);

    /** 测试清理用 */
    @org.apache.ibatis.annotations.Delete("DELETE FROM bag WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
