package com.lootrush.reconciliation;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 对账差异登记行：同一差异（类型+定位）只登记一次（唯一索引） */
@Data
public class ReconDiffEntity {
    private Long id;
    private LocalDate reconDate;
    private String diffType;
    private String orderNo;
    private String detail;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
