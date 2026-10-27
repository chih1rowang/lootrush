package com.lootrush.gacha;

import lombok.Data;

import java.time.LocalDateTime;

/** 抽卡流水（只增不改）：每一抽一行，含出卡后的保底计数——概率审计的原始账本 */
@Data
public class GachaRecordEntity {
    private Long id;
    private Long userId;
    private Long itemId;
    private Integer rarity;
    private Integer pityCountAfter;
    private Integer isPityHit;
    private String batchNo;
    private LocalDateTime createTime;
}
