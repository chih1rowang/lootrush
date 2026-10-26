package com.lootrush.trade;

import lombok.Data;

import java.time.LocalDateTime;

/** 库存流水（只增不改）：库存每一次增减一行——循环7对账"两边库存核对"的数据源 */
@Data
public class StockFlowEntity {
    private Long id;
    private Long goodsId;
    private Integer changeCount;
    private String reason;
    private String orderNo;
    private LocalDateTime createTime;
}
