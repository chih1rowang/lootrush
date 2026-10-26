package com.lootrush.trade.callback;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 回调验签：支付公司和我们约定——
 * sign = HMAC-SHA256(共享密钥, "orderNo|amount|notifyNo") 的十六进制小写。
 * 只有持有同一密钥的双方能算出合法签名，伪造的通知在这里被拒。
 * （与 mock-payment 里的 HmacUtil 是同一套算法——两家"公司"只共享密钥与签名规范。）
 */
public class HmacUtil {

    public static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC计算失败", e);
        }
    }

    public static boolean verify(String secret, String data, String sign) {
        return MessageDigest.isEqual(
                hmacSha256Hex(secret, data).getBytes(StandardCharsets.UTF_8),
                (sign == null ? "" : sign.toLowerCase()).getBytes(StandardCharsets.UTF_8));
    }
}
