package com.lootrush.rank;

import lombok.Data;

/** 排行榜回填用的消费合计行 */
@Data
public class ConsumptionRow {
    private Long userId;
    private Long value;
}
