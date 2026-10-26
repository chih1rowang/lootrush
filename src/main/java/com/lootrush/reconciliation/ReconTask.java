package com.lootrush.reconciliation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每日凌晨2点定时对账（可用 RECON_CRON 环境变量覆盖）；多实例下由锁保证单实例执行 */
@Component
public class ReconTask {

    private static final Logger log = LoggerFactory.getLogger(ReconTask.class);

    private final ReconService reconService;

    public ReconTask(ReconService reconService) {
        this.reconService = reconService;
    }

    @Scheduled(cron = "${recon.cron:0 0 2 * * ?}")
    public void dailyRecon() {
        log.info("每日对账任务启动");
        reconService.runOnce("定时");
    }
}
