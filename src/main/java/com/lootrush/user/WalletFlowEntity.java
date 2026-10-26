package com.lootrush.user;

import lombok.Data;

import java.time.LocalDateTime;

/** 钱包流水（只增不改）：钻石每一次增减一行——对账与审计的事实来源 */
@Data
public class WalletFlowEntity {
    private Long id;
    private Long userId;
    private String changeType;
    private Integer amount;
    private Integer balanceAfter;
    private String refOrderNo;
    private LocalDateTime createTime;
}
