package com.lootrush.goods;

import lombok.Data;

import java.time.LocalDateTime;

/** 货架联查结果：商品列 + 道具列摊平在一张表里 */
@Data
public class GoodsFlat {
    private Long id;
    private Long itemId;
    private String name;
    private String description;
    private String imageUrl;
    private Integer priceDiamond;
    private Integer originalPrice;
    private Integer stockTotal;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
}
