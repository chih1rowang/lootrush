package com.lootrush.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;

/** Lua 脚本注册：deduct_stock.lua 见 src/main/resources/scripts/ */
@Configuration
public class ScriptConfig {

    @Bean
    public DefaultRedisScript<Long> deductStockScript() throws Exception {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        try (var in = new ClassPathResource("scripts/deduct_stock.lua").getInputStream()) {
            script.setScriptText(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        script.setResultType(Long.class);
        return script;
    }
}
