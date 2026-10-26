package com.lootrush.trade;

import lombok.Data;

/** 背包行（道具+数量） */
@Data
public class BagEntity {
    private Long id;
    private Long userId;
    private Long itemId;
    private Integer count;
}
