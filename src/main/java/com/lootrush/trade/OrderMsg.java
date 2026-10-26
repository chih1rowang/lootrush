package com.lootrush.trade;

/** ORDER_CREATED 消息体（生产者/消费者共用） */
public record OrderMsg(String orderNo, Long userId, Long goodsId, int priceDiamond) {
}
