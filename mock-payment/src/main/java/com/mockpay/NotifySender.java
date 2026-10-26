package com.mockpay;

import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 回调发送器：按支付行业的方式 POST 通知到商户（LootRush），带 HMAC 签名 */
public class NotifySender {

    private final String callbackUrl;
    private final String secret;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotifySender(String callbackUrl, String secret) {
        this.callbackUrl = callbackUrl;
        this.secret = secret;
    }

    public record CallbackResult(int httpStatus, String body) {
    }

    /** 同一条通知发 N 次（notifyNo 相同）：REPEAT 作恶模式的实现载体 */
    public CallbackResult send(String orderNo, int amount, String notifyNo) {
        String data = orderNo + "|" + amount + "|" + notifyNo;
        String sign = hmac(data);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("notifyNo", notifyNo);
        body.put("orderNo", orderNo);
        body.put("amount", amount);
        body.put("sign", sign);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(callbackUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return new CallbackResult(resp.statusCode(), resp.body());
        } catch (Exception e) {
            return new CallbackResult(-1, "回调失败: " + e.getMessage());
        }
    }

    public CallbackResult send(String orderNo, int amount) {
        return send(orderNo, amount, UUID.randomUUID().toString().replace("-", ""));
    }

    private String hmac(String data) {
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
            throw new IllegalStateException(e);
        }
    }
}
