package com.mockpay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 假支付公司的"对外合同"配置 */
@Configuration
public class MockPayConfig {

    @Bean
    public NotifySender notifySender(@Value("${pay.callback-url}") String callbackUrl,
                                     @Value("${pay.secret}") String secret) {
        return new NotifySender(callbackUrl, secret);
    }
}
