package com.lootrush.trade.callback;

import com.lootrush.common.ApiResponse;
import com.lootrush.trade.PayService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付回调入口（信任级别最低的门）：
 * - 不走 JWT（在 WebConfig 白名单里）——支付公司不是我们的玩家；
 * - 靠 HMAC 验签自证身份；
 * - 以后压测时会单独给它配限流参数。
 */
@RestController
@RequestMapping("/api/pay")
public class PayCallbackController {

    private final PayService payService;

    public PayCallbackController(PayService payService) {
        this.payService = payService;
    }

    @PostMapping("/callback")
    public ApiResponse<Void> callback(@RequestBody PayService.PayCallbackReq req) {
        return payService.processCallback(req);
    }
}
