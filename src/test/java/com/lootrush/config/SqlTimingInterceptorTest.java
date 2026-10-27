package com.lootrush.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lootrush.user.UserEntity;
import com.lootrush.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 循环9B 测试：MyBatis 慢SQL拦截器。
 * 把阈值设为 0（本测试专用配置），任何一条 SQL 都会被判"慢"，
 * 再用 Logback 的 ListAppender 捕获拦截器打出的日志，验证 WARN 真的发生了。
 */
@SpringBootTest(properties = "sql-trace.slow-ms=0")
@Transactional
class SqlTimingInterceptorTest {

    @Autowired UserMapper userMapper;

    @Test
    void slow_sql_is_logged_as_warn_and_query_still_works() {
        // 挂一个内存日志收集器到拦截器的 logger 上
        Logger interceptorLogger = (Logger) LoggerFactory.getLogger(SqlTimingInterceptor.class);
        interceptorLogger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        interceptorLogger.addAppender(appender);
        try {
            // 造一个用户并查询：阈值=0，这条 SELECT 必然被判慢
            UserEntity u = new UserEntity();
            u.setUsername("slowsql" + System.currentTimeMillis() % 1000000);
            u.setPasswordHash("$2a$10$test");
            u.setDiamond(100);
            userMapper.insert(u);
            UserEntity found = userMapper.selectByUsername(u.getUsername());
            assertNotNull(found, "拦截器只旁观不干预：查询必须正常返回");
        } finally {
            interceptorLogger.detachAppender(appender);
        }

        boolean hasSlowWarn = appender.list.stream()
                .anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("慢SQL"));
        assertTrue(hasSlowWarn, "阈值=0时至少应有一条慢SQL WARN，实际捕获条数=" + appender.list.size());
    }
}
