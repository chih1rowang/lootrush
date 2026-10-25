package com.lootrush.goods;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface ItemMapper {

    @Insert("INSERT INTO item(name, rarity, source_type, description, image_url) " +
            "VALUES(#{name}, #{rarity}, #{sourceType}, #{description}, #{imageUrl})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ItemEntity item);
}
