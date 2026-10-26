package com.lootrush.trade;

import lombok.Data;

import java.time.LocalDateTime;

/** 订单表的一行。状态流转：0待支付→1已支付→2已发货；0→3已关闭 */
@Data
public class OrderEntity {
    private Long id;
    private String orderNo;
    private String type;
    private Long userId;
    private Long goodsId;
    private Integer priceDiamond;
    private Integer status;
    private Long activeFlag;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
