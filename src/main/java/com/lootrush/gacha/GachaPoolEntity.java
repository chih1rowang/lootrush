package com.lootrush.gacha;

import lombok.Data;

/** 卡池行：一件道具及其权重（概率 = weight / Σweight，页面对外公示） */
@Data
public class GachaPoolEntity {
    private Long id;
    private Long itemId;
    private Integer rarity;
    private Integer weight;
}
