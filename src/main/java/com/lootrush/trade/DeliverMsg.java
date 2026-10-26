package com.lootrush.trade;

/** DELIVER_GOODS 消息体 */
public record DeliverMsg(String orderNo, Long userId, Long itemId, int qty) {

    public static final String TOPIC = "DELIVER_GOODS";
}
