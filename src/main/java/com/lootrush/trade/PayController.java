package com.lootrush.trade;

import com.lootrush.common.ApiResponse;
import com.lootrush.common.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** 充值与支付跳转（JWT 保护） */
@RestController
@RequestMapping("/api/pay")
@Validated
public class PayController {

    private final OrderMapper orderMapper;
    private final String mockPayBaseUrl;

    public PayController(OrderMapper orderMapper,
                         @Value("${mock-pay.base-url:http://localhost:18090}") String mockPayBaseUrl) {
        this.orderMapper = orderMapper;
        this.mockPayBaseUrl = mockPayBaseUrl;
    }

    /** 发起充值：建一张 RECHARGE 订单（goods_id=0，active_flag=NULL 不受限购约束），返回支付页地址 */
    public record RechargeReq(@NotNull @Min(1) @Max(1000000) Integer amount) {
    }

    @PostMapping("/recharge")
    public ApiResponse<Map<String, String>> recharge(@Validated @RequestBody RechargeReq req,
                                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String orderNo = "R" + System.currentTimeMillis()
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000)) + userId;
        OrderEntity order = new OrderEntity();
        order.setOrderNo(orderNo);
        order.setType("RECHARGE");
        order.setUserId(userId);
        order.setGoodsId(0L);
        order.setPriceDiamond(req.amount());
        order.setStatus(0);
        order.setActiveFlag(null); // 充值单不占"一人一单"名额
        orderMapper.insert(order);
        return ApiResponse.ok(Map.of(
                "orderNo", orderNo,
                "payUrl", mockPayBaseUrl + "/pay.html?orderNo=" + orderNo + "&amount=" + req.amount()));
    }

    /** 购买订单的支付页地址（玩家在订单页点"去支付"用） */
    @GetMapping("/page/{orderNo}")
    public ApiResponse<Map<String, String>> payPage(@PathVariable String orderNo,
                                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        OrderEntity order = orderMapper.selectByOrderNo(orderNo);
        if (order == null || !order.getUserId().equals(userId)) {
            throw BizException.of(404, "订单不存在");
        }
        return ApiResponse.ok(Map.of(
                "payUrl", mockPayBaseUrl + "/pay.html?orderNo=" + orderNo + "&amount=" + order.getPriceDiamond()));
    }
}
