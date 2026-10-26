package com.mockpay;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 假支付公司的收银台 API：
 * - POST /mock-pay/confirm?orderNo=&amount=  玩家点"确认付款"——按当前作恶开关执行回调
 * - GET  /mock-pay/switch                    看当前开关
 * - POST /mock-pay/switch?mode=NORMAL|DELAY|REPEAT   切换开关
 */
@RestController
public class MockPayController {

    private final EvilSwitch evilSwitch;
    private final NotifySender sender;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public MockPayController(EvilSwitch evilSwitch, NotifySender sender) {
        this.evilSwitch = evilSwitch;
        this.sender = sender;
    }

    @PostMapping("/mock-pay/confirm")
    public Map<String, Object> confirm(@RequestParam String orderNo, @RequestParam int amount) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("mode", evilSwitch.get().name());
        switch (evilSwitch.get()) {
            case NORMAL -> {
                NotifySender.CallbackResult r = sender.send(orderNo, amount);
                resp.put("sent", 1);
                resp.put("merchantReply", r.body());
            }
            case REPEAT -> {
                // 现实事故模型：网络抖动让支付公司把同一条通知重发。三发共用一个 notifyNo。
                String notifyNo = java.util.UUID.randomUUID().toString().replace("-", "");
                for (int i = 0; i < 3; i++) {
                    scheduler.schedule(() -> sender.send(orderNo, amount, notifyNo), i * 2L, TimeUnit.SECONDS);
                }
                resp.put("sent", 3);
                resp.put("notifyNo", notifyNo);
                resp.put("note", "同一条通知将在6秒内到达3次——商户必须幂等");
            }
            case DELAY -> {
                scheduler.schedule(() -> sender.send(orderNo, amount), 30, TimeUnit.SECONDS);
                resp.put("note", "回调已安排在30秒后到达");
            }
        }
        return resp;
    }

    @GetMapping("/mock-pay/switch")
    public Map<String, Object> getSwitch() {
        return Map.of("mode", evilSwitch.get().name());
    }

    @PostMapping("/mock-pay/switch")
    public Map<String, Object> setSwitch(@RequestParam String mode) {
        EvilSwitch.Mode m = EvilSwitch.Mode.valueOf(mode.toUpperCase());
        evilSwitch.set(m);
        return Map.of("mode", evilSwitch.get().name());
    }
}
