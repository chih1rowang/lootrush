package com.lootrush.config;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加解密工具（敏感字段的算法底座）。
 *
 * 密文格式：enc:v1: + Base64(随机IV(12字节) || 密文+认证标签(128位))
 * - 随机 IV：同一明文每次加密产物都不同（防频率分析）；代价是无法等值检索——
 *   实名信息业务上不需要模糊查，代价可接受（要等值检索需另建 HMAC 确定性索引列）。
 * - GCM 自带完整性校验：密文被篡改解密即失败。
 * - "enc:v1:" 前缀三重用途：解密只认自家密文（其他内容原样返回）、防重复加密、
 *   未来换算法时可平滑升级为 v2（灰度共存）。
 * - 密钥管理：开发期配置注入；生产应上 KMS/配置中心（边界诚实声明）。
 */
public final class AesGcmUtil {

    public static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec keySpec;

    public AesGcmUtil(String keyConfig) {
        // 支持32字符原文或Base64；长度不符直接快速失败（密钥错误必须启动期暴露）
        byte[] keyBytes;
        if (keyConfig != null && keyConfig.getBytes(StandardCharsets.UTF_8).length == 32) {
            keyBytes = keyConfig.getBytes(StandardCharsets.UTF_8);
        } else {
            keyBytes = Base64.getDecoder().decode(keyConfig);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException("AES密钥必须是32字节，实际" + keyBytes.length);
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    /** 明文 → 密文（带前缀）。已是密文的输入原样返回（防重复加密）。 */
    public String encrypt(String plain) {
        if (plain == null || plain.isBlank() || plain.startsWith(PREFIX)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /** 密文（带前缀）→ 明文。非自家密文的输入原样返回（防重复解密）。 */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec,
                    new GCMParameterSpec(TAG_BITS, all, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败（密文被篡改或密钥不匹配）", e);
        }
    }
}
