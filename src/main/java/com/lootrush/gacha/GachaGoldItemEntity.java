package com.lootrush.gacha;

import lombok.Data;

import java.time.LocalDateTime;

/** 金装轮换队列行：按 slot_order 排队，每期上场 5 件，按周轮换 */
@Data
public class GachaGoldItemEntity {
    private Long id;
    private Long itemId;
    private Integer slotOrder;
}
