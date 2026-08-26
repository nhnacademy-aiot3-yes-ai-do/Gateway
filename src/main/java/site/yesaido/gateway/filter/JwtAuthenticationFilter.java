package site.yesaido.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
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
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/users/signup",
            "/api/v1/users/check-email",
            "/api/v1/users/check-nickname",
            "/api/v1/auth/email",
            "/api/v1/auth/dormant/release",
            "/api/v1/auth/reissue",
            "/api/v1/auth/oauth2",
            "/api/v1/auth/oauth2/google"
    );

    private final Key key;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String accessToken = resolveAccessToken(exchange.getRequest());
        if(accessToken == null || accessToken.isBlank()){
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(accessToken).getBody();
            String role = claims.get("role", String.class);

            ServerHttpRequest.Builder mutatedBuilder = exchange.getRequest().mutate();
            mutatedBuilder.headers(headers -> {
                headers.set("X-User-Id", claims.getSubject());
                if (role != null) {
                    headers.set("X-User-Role", role);
                } else {
                    headers.remove("X-User-Role");
                }
            });

            return chain.filter(exchange.mutate().request(mutatedBuilder.build()).build());
        } catch (JwtException | IllegalArgumentException e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    // AccessToken 추출
    private String resolveAccessToken(ServerHttpRequest request){
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if(authorization != null && authorization.startsWith("Bearer ")){
            return authorization.substring(7);
        }

        HttpCookie accessTokenCookie = request.getCookies().getFirst("accessToken");

        return accessTokenCookie != null ? accessTokenCookie.getValue() : null;
    }

    // -2: 인증 . -1: 인가 -> 인증 필터 후 인가 필터
    @Override
    public int getOrder() {
        return -2;
    }
}
