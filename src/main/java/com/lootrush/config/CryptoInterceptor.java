package com.lootrush.config;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 敏感字段透明加解密插件（简历行本体，循环9C）。
 *
 * 为什么拦 Executor 而不是 StatementHandler（9B 的工位）：
 * Executor 层的参数与返回值是【业务对象】——可以直接反射处理带
 * {@link EncryptedField} 注解的字段；StatementHandler 层只剩 SQL 字符串和
 * 参数对象，做字段级加解密要反解析 SQL，得不偿失。两个插件各守一层，
 * 恰好展示同一机制在不同工位的不同用法。
 *
 * 行为：
 * - update 前扫描参数对象：带注解的 String 字段自动加密（写库即密文）；
 * - query 后扫描返回 List 的每个元素：带注解字段自动解密（业务拿到的永远是明文）；
 * - 业务 Mapper 零改动：字段级注解驱动，反射元数据按类缓存（反射只走一遍）。
 *
 * 已知边界（面试弹药）：Map 参数形式的语句不在处理范围（本项目统一用实体参数）；
 * 解密失败按 ERROR 落日志并保留密文（展示层可见异常但不炸页面）。
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, org.apache.ibatis.mapping.BoundSql.class})
})
@Component
public class CryptoInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(CryptoInterceptor.class);

    private final AesGcmUtil aes;
    private final Map<Class<?>, List<Field>> fieldsCache = new ConcurrentHashMap<>();

    public CryptoInterceptor(@Value("${crypto.aes-key}") String keyConfig) {
        this.aes = new AesGcmUtil(keyConfig);
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if ("update".equals(invocation.getMethod().getName())) {
            Object param = invocation.getArgs()[1];
            process(param, true);
            return invocation.proceed();
        }
        Object result = invocation.proceed();
        if (result instanceof List<?> list) {
            for (Object element : list) {
                process(element, false);
            }
        }
        return result;
    }

    private void process(Object bean, boolean encrypt) {
        if (bean == null || bean instanceof Map || bean instanceof String
                || bean instanceof Number || bean instanceof Boolean) {
            return; // 实体参数才处理；@Param 的 Map 与简单类型跳过（边界声明）
        }
        List<Field> fields = encryptedFieldsOf(bean.getClass());
        for (Field f : fields) {
            try {
                String value = (String) f.get(bean);
                if (value == null) {
                    continue;
                }
                f.set(bean, encrypt ? aes.encrypt(value) : aes.decrypt(value));
            } catch (Exception e) {
                log.error("敏感字段{}处理失败（{}）", f.getName(), encrypt ? "加密" : "解密", e);
            }
        }
    }

    private List<Field> encryptedFieldsOf(Class<?> clazz) {
        return fieldsCache.computeIfAbsent(clazz, c -> {
            List<Field> out = new ArrayList<>();
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (f.isAnnotationPresent(EncryptedField.class) && f.getType() == String.class) {
                        f.setAccessible(true);
                        out.add(f);
                    }
                }
            }
            return out;
        });
    }

    @Override
    public Object plugin(Object target) {
        return target instanceof Executor ? Plugin.wrap(target, this) : target;
    }
}
