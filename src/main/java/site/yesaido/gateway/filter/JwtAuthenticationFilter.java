package site.yesaido.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Key;
import java.util.List;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    private static final List<String> PUBLIC_PATHS = List.of("/api/auth/login", "/api/users/signup", "/api/users/check-email", "/api/users/check-nickname");
    private static final String CLIENT_IP_HEADER = "X-Real-Client-Ip";

    private final Key key;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String resolvedClientIp = resolveClientIp(exchange.getRequest());

        ServerHttpRequest requestWithClientIp = exchange.getRequest().mutate()
                .headers(headers -> headers.set(CLIENT_IP_HEADER, resolvedClientIp))
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(requestWithClientIp).build();

        String path = requestWithClientIp.getURI().getPath();
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(mutatedExchange);
        }

        String authHeader = requestWithClientIp.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            mutatedExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return mutatedExchange.getResponse().setComplete();
        }

        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(authHeader.substring(7)).getBody();
            ServerHttpRequest finalRequest = requestWithClientIp.mutate()
                    .header("X-User-Id", claims.getSubject())
                    .build();
            return chain.filter(mutatedExchange.mutate().request(finalRequest).build());
        } catch (JwtException | IllegalArgumentException e) {
            mutatedExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return mutatedExchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }

    // headers.set()이 클라이언트가 직접 보낸 X-Real-Client-Ip 값을 무조건 덮어씀
    private String resolveClientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}