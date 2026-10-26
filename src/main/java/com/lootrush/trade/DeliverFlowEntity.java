package com.lootrush.trade;

import lombok.Data;

import java.time.LocalDateTime;

/** 发货记录（只增不改）：order_no 唯一 = 一单只发一次的幂等锚点 */
@Data
public class DeliverFlowEntity {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long itemId;
    private Integer qty;
    private LocalDateTime createTime;
}
