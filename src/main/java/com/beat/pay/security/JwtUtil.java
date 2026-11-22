package com.beat.pay.security;

import com.beat.pay.common.BizException;
import com.beat.pay.common.ErrorCode;
import com.beat.pay.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** Access Token（JWT，HS256）：sub=userId，sid=sessionId，did=deviceId。吊销依赖 sid 对应会话状态校验 */
@Component
public class JwtUtil {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtUtil(JwtProperties props) {
        this.props = props;
        if (props.getSecret() == null || props.getSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("beat.jwt.secret 必须配置且不少于 32 字节（请用环境变量 JWT_SECRET）");
        }
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, Long sessionId, String deviceId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(String.valueOf(userId))
                .claim("sid", String.valueOf(sessionId))
                .claim("did", deviceId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + props.getAccessTtlSeconds() * 1000L))
                .signWith(key)
                .compact();
    }

    /** 解析并校验；过期 -> ACCESS_TOKEN_EXPIRED，其他 -> TOKEN_INVALID */
    public AuthPrincipal parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).requireIssuer(props.getIssuer()).build()
                    .parseSignedClaims(token).getPayload();
            return new AuthPrincipal(Long.valueOf(c.getSubject()), Long.valueOf(c.get("sid", String.class)),
                    c.get("did", String.class));
        } catch (ExpiredJwtException e) {
            throw new BizException(ErrorCode.ACCESS_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
    }
}
