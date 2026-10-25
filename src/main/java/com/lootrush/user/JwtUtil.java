package com.lootrush.user;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT 通行证：签发与验签。
 * JWT 是"带签名的电子通行证"——三段式：头部(算法).载荷(userId等).签名(防篡改)。
 * 服务端不用存它（无状态），只靠密钥验签；代价是签发后无法提前作废，只能等它过期。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire-minutes:720}") long expireMinutes) {
        // 密钥至少 32 字节，配置在 application.yml
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expireMillis = expireMinutes * 60 * 1000;
    }

    /** 给指定用户签发一张通行证（默认 12 小时有效） */
    public String issue(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /** 验签并取回 userId；伪造/过期的通行证会在这里抛异常 */
    public Long parseUserId(String token) {
        String subject = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getSubject();
        return Long.valueOf(subject);
    }
}
