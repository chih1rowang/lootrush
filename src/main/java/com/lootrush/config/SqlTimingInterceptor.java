package com.lootrush.config;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Statement;

/**
 * 自定义 MyBatis 拦截器（俗称"插件"）：给每一条 SQL 掐表。
 *
 * MyBatis 的插件机制 = 动态代理：对 StatementHandler（真正执行 JDBC 的工位）
 * 生成代理对象，query/update 每次执行都会先经过 intercept()。
 *
 * 用途（循环10压测的主配合件）：压测时把阈值调低（SQL_SLOW_MS 环境变量），
 * 慢 SQL 自动在日志里排队现身，定位"慢在哪条 SQL"不用再靠猜。
 * 为什么自研而非引 p6spy：三十行零依赖拿到核心能力，输出格式完全可控。
 *
 * 阈值以上的打 WARN（慢SQL），以下的只打 DEBUG（平时安静不刷屏）。
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query",
                args = {Statement.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update",
                args = {Statement.class})
})
@Component
public class SqlTimingInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(SqlTimingInterceptor.class);

    private final long slowMs;

    public SqlTimingInterceptor(@Value("${sql-trace.slow-ms:300}") long slowMs) {
        this.slowMs = slowMs;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            StatementHandler handler = (StatementHandler) invocation.getTarget();
            String sql = handler.getBoundSql().getSql().replaceAll("\\s+", " ").trim();
            if (cost >= slowMs) {
                log.warn("慢SQL {}ms | {} | 参数={}", cost, sql, handler.getBoundSql().getParameterObject());
            } else {
                log.debug("SQL {}ms | {}", cost, sql);
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        // 只对 StatementHandler 生成代理，其他工位原样放行
        return target instanceof StatementHandler ? Plugin.wrap(target, this) : target;
    }
}
