package com.lootrush.trade;

import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 循环3 开工纪律：先验证 rocketmq-spring-boot-starter 2.3.5 与 Spring Boot 4 的兼容性。
 * 三关：① 上下文能起（自动装配不炸）② RocketMQTemplate 能发到本机 broker
 *      ③ 消费者能收到同一条消息。三关全过才允许后续开发用它。
 */
@SpringBootTest
class RocketMqCompatTest {

    @Autowired
    org.apache.rocketmq.spring.core.RocketMQTemplate template;

    @Test
    void send_and_receive_through_live_broker() throws Exception {
        String topic = "LOOTRUSH_COMPAT_TEST";
        String payload = "compat-" + UUID.randomUUID();

        // 关①已隐含：能注入 RocketMQTemplate 说明自动装配成功
        template.syncSend(topic, MessageBuilder.withPayload(payload).build());

        // 关③：用轻量拉取消费者直接从 broker 收
        DefaultLitePullConsumer consumer = new DefaultLitePullConsumer("compat-test-group");
        consumer.setNamesrvAddr("localhost:9876");
        consumer.subscribe(topic, "*");
        consumer.start();
        try {
            String got = null;
            for (int i = 0; i < 10 && got == null; i++) {
                List<MessageExt> msgs = consumer.poll();
                for (MessageExt m : msgs) {
                    String body = new String(m.getBody(), StandardCharsets.UTF_8);
                    if (body.contains(payload)) {
                        got = body;
                    }
                }
            }
            assertNotNull(got, "10秒内应收到刚发送的消息");
            assertEquals(payload, got);
        } finally {
            consumer.shutdown();
        }
    }
}
