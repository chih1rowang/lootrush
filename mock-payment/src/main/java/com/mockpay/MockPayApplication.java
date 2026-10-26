package com.mockpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 假支付公司：独立进程（端口18090），和主系统只共享"回调地址+密钥+签名规范" */
@SpringBootApplication
public class MockPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockPayApplication.class, args);
    }
}
