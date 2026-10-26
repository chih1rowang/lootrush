package com.lootrush.trade;

import com.lootrush.config.LockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 扫表兜底任务（技术总表场景10的"兜底"、场景11的分布式锁现场）：
 * 延迟消息是主力，但万一消息丢失/消费者宕机期间订单过期，这里按固定频率扫描
 * 超时未付订单补刀。两套机制都靠状态机守卫保证幂等，谁先到谁生效。
 *
 * 双实例演示：起两个进程（8080/8081）同时运行本任务——没有锁会两边都扫；
 * LockService 的 SET NX 保证每轮只有一个实例干活（另一个直接跳过），看日志即知。
 */
@Component
public class OrderCloseScanTask {

    private static final Logger log = LoggerFactory.getLogger(OrderCloseScanTask.class);

    private final OrderMapper orderMapper;
    private final OrderCloseService closeService;
    private final LockService lockService;

    @Value("${order.close-minutes:20}")
    private int closeMinutes;

    public OrderCloseScanTask(OrderMapper orderMapper, OrderCloseService closeService,
                              LockService lockService) {
        this.orderMapper = orderMapper;
        this.closeService = closeService;
        this.lockService = lockService;
    }

    /** 每轮先抢锁，抢不到说明另一实例正在扫（本轮放弃）。fixedDelay=上一轮结束后再计时。 */
    @Scheduled(fixedDelayString = "${order.close-scan-seconds:60}000")
    public void scan() {
        if (!lockService.tryLock(LockService.ORDER_CLOSE_LOCK, Duration.ofSeconds(30))) {
            log.info("本轮扫表由其他实例执行，跳过");
            return;
        }
        try {
            var expired = orderMapper.selectExpiredUnpaid(LocalDateTime.now().minusMinutes(closeMinutes));
            if (!expired.isEmpty()) {
                log.info("扫表发现{}张超时未付订单", expired.size());
            }
            for (OrderEntity o : expired) {
                closeService.closeIfUnpaid(o.getOrderNo(), "扫表兜底");
            }
        } finally {
            lockService.unlock(LockService.ORDER_CLOSE_LOCK);
        }
    }
}
