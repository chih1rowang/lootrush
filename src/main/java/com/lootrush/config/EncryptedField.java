package com.lootrush.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感字段标记：实体上打此注解的 String 字段，由 CryptoInterceptor 在
 * 写库前自动 AES-GCM 加密、读库后自动解密（业务 Mapper 零侵入）。
 * 配套脱敏展示见 MaskUtil。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EncryptedField {
}
