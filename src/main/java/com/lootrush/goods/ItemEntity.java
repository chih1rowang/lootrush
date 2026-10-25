package com.lootrush.goods;

import lombok.Data;

import java.time.LocalDateTime;

/** 道具字典表的一行（抽卡循环会复用这张表） */
@Data
public class ItemEntity {
    private Long id;
    private String name;
    private Integer rarity;
    private String sourceType;
    private String description;
    private String imageUrl;
    private LocalDateTime createTime;
}
