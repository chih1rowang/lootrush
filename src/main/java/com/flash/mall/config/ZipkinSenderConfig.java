package com.flash.mall.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * 手工打造"行程单运输车"。
 *
 * 为什么需要这个类：Spring Boot 4 不再自动创建发往 Zipkin 的运输车
 * （BytesMessageSender），官方改为要求使用者按自己选的运输方式
 * （这里用 JDK 自带的 HTTP 能力，即 URLConnection）自行声明。
 * 没有这个零件，行程单永远发不出去、且不报任何错。
 */
@Configuration
public class ZipkinSenderConfig {

    @Bean
    public BytesMessageSender zipkinSender(
            @Value("${management.zipkin.tracing.endpoint:http://localhost:9411/api/v2/spans}")
            String endpoint) {
        return URLConnectionSender.newBuilder()
                .endpoint(endpoint)
                .build();
    }

    /**
     * 行程单的编码原料：用 JSON 格式（Zipkin 网页端认识的标准格式）。
     * Boot 4 的发报机组装线要求使用方先备好这份原料。
     */
    @Bean
    public Encoding zipkinEncoding() {
        return Encoding.JSON;
    }
}
