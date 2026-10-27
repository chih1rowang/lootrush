package com.lootrush.gacha;

import lombok.Data;

import java.time.LocalDateTime;

/** 抽卡记录展示行（联查道具名） */
@Data
public class GachaRecordFlat {
    private Long id;
    private Long userId;
    private Long itemId;
    private String itemName;
    private Integer rarity;
    private Integer pityCountAfter;
    private Integer isPityHit;
    private String batchNo;
    private LocalDateTime createTime;
}
