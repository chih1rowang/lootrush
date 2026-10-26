package com.lootrush.trade;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 延迟关单消息消费者：延迟级别到期才被投递（创建订单时发出的"定时炸弹"）。
 * 到点后交给 OrderCloseService——状态机守卫保证：已付款的订单安然无恙。
 */
@Component
@RocketMQMessageListener(topic = OrderCloseService.TOPIC_ORDER_CLOSE_DELAY,
        consumerGroup = "lootrush-close-group")
public class OrderCloseListener implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(OrderCloseListener.class);

    private final OrderCloseService closeService;

    public OrderCloseListener(OrderCloseService closeService) {
        this.closeService = closeService;
    }

    @Override
    public void onMessage(String orderNo) {
        log.info("延迟关单消息到点 orderNo={}", orderNo);
        closeService.closeIfUnpaid(orderNo, "延迟消息");
    }
}
