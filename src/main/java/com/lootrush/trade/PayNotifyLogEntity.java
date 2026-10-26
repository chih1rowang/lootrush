package com.lootrush.trade;

import lombok.Data;

import java.time.LocalDateTime;

/** 支付通知日志（只增不改）：每条通知一行，result 记录处理结局——幂等的证据表 */
@Data
public class PayNotifyLogEntity {
    private Long id;
    private String notifyNo;
    private String orderNo;
    private Integer amount;
    private String result;
    private LocalDateTime createTime;
}
