package com.lootrush.goods;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface GoodsMapper {

    @Insert("INSERT INTO goods(item_id, price_diamond, original_price, stock_total, sale_start_time, sale_end_time) " +
            "VALUES(#{itemId}, #{priceDiamond}, #{originalPrice}, #{stockTotal}, #{saleStartTime}, #{saleEndTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GoodsEntity goods);

    /** 商品+道具的联查结果（货架展示需要的所有列一次拿全） */
    @Select("SELECT g.id, g.item_id AS itemId, i.name, i.description, i.image_url AS imageUrl, " +
            "i.rarity, g.price_diamond AS priceDiamond, g.original_price AS originalPrice, " +
            "g.stock_total AS stockTotal, g.sale_start_time AS saleStartTime, g.sale_end_time AS saleEndTime " +
            "FROM goods g JOIN item i ON g.item_id = i.id " +
            "ORDER BY g.sale_start_time")
    List<GoodsFlat> selectAllWithItem();

    @Select("SELECT * FROM goods WHERE id = #{id}")
    GoodsEntity selectById(@Param("id") Long id);

    @Update("UPDATE goods SET price_diamond = #{price} WHERE id = #{id}")
    int updatePrice(@Param("id") Long id, @Param("price") int price);
}
