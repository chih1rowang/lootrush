package com.lootrush.reconciliation;

import lombok.Data;

/** 库存推导结果行：数据库事实源认为 Redis 应该是多少 */
@Data
public class ExpectedStockRow {
    private Long goodsId;
    private Long expectedStock;
}
