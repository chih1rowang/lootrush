package com.lootrush.goods;

import lombok.Data;

import java.time.LocalDateTime;

/** 贩售商品表的一行。注意：四态（即将开售/抢购中/已售罄/已结束）不在这里，
 *  每次用"当前时间 + 实时库存"推导——存下来的状态会和事实打架，算出来的不会。 */
@Data
public class GoodsEntity {
    private Long id;
    private Long itemId;
    private Integer priceDiamond;
    private Integer originalPrice;
    private Integer stockTotal;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
