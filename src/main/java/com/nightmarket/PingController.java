package com.nightmarket;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第一个验证接口：用来确认「程序活着、链路追踪和监控都在工作」。
 * 浏览器访问 http://localhost:8080/ping 应该看到 pong。
 */
@RestController
public class PingController {

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
