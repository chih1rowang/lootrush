package com.lootrush.trade;

import com.lootrush.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 抢购与订单接口（JWT 保护） */
@RestController
@RequestMapping("/api")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping("/purchase/{goodsId}")
    public ApiResponse<Map<String, String>> purchase(@PathVariable Long goodsId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String orderNo = purchaseService.purchase(userId, goodsId);
        return ApiResponse.ok(Map.of("orderNo", orderNo));
    }

    @GetMapping("/order/my")
    public ApiResponse<List<OrderEntity>> myOrders(HttpServletRequest request) {
        return ApiResponse.ok(purchaseService.myOrders((Long) request.getAttribute("userId")));
    }
}
