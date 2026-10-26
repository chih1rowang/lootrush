package com.lootrush.trade;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * DELIVER_GOODS 消费者（技术总表场景9）：
 * 发奖三动作在一个事务里——记发货流水、背包入道具、订单标记已发货。
 *
 * 幂等两道防线：
 * 1. deliver_flow.order_no 唯一索引：同一单的第二条消息插不进 → 直接忽略；
 * 2. 状态机 1(已支付)→2(已发货) 条件更新：0 行 = 已发过/已异常，不再动背包。
 * 消息重投时两道防线联合保证"道具绝不发两次"。
 */
@Component
@RocketMQMessageListener(topic = DeliverMsg.TOPIC, consumerGroup = "lootrush-deliver-group")
public class DeliverGoodsListener implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(DeliverGoodsListener.class);

    private final DeliverFlowMapper deliverFlowMapper;
    private final BagMapper bagMapper;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeliverGoodsListener(DeliverFlowMapper deliverFlowMapper, BagMapper bagMapper,
                                OrderMapper orderMapper) {
        this.deliverFlowMapper = deliverFlowMapper;
        this.bagMapper = bagMapper;
        this.orderMapper = orderMapper;
    }

    @Override
    @Transactional
    public void onMessage(String json) {
        DeliverMsg msg;
        try {
            msg = objectMapper.readValue(json, DeliverMsg.class);
        } catch (Exception e) {
            log.error("发货消息解析失败，丢弃：{}", json, e);
            return;
        }

        DeliverFlowEntity flow = new DeliverFlowEntity();
        flow.setOrderNo(msg.orderNo());
        flow.setUserId(msg.userId());
        flow.setItemId(msg.itemId());
        flow.setQty(msg.qty());
        try {
            deliverFlowMapper.insert(flow); // 幂等锚点：一单只允许一行
        } catch (DuplicateKeyException e) {
            log.warn("重复的发货消息，幂等忽略 orderNo={}", msg.orderNo());
            return;
        }

        bagMapper.upsertAdd(msg.userId(), msg.itemId(), msg.qty());

        if (orderMapper.markDeliveredIfPaid(msg.orderNo()) == 0) {
            // 理论上到不了这里（消息只在支付成功后发出）；出现则说明数据异常，留日志供对账
            log.error("发货时订单状态不是'已支付'，请对账排查 orderNo={}", msg.orderNo());
        }
        log.info("发货完成 orderNo={} user={} item={} qty={}",
                msg.orderNo(), msg.userId(), msg.itemId(), msg.qty());
    }
}
