package com.lootrush.trade;

import lombok.Data;

/** 背包展示行（联查道具信息） */
@Data
public class BagFlat {
    private Long itemId;
    private String name;
    private Integer rarity;
    private String imageUrl;
    private Integer count;
}
