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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import site.yesaido.gateway.service.AccessTokenBlacklistService;

import java.security.Key;
import java.util.List;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    private static final String TELEGRAM_WEBHOOK_PATH = "/webhooks/telegram";
    private static final List<String> PUBLIC_EXACT_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
            "/api/v1/users/signup",
            "/api/v1/users/signup/verify-email",
            "/api/v1/users/check-nickname",
            "/api/v1/auth/dormant/release",
            "/api/v1/auth/reissue",
            "/api/v1/auth/password/reset"
    );
    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/api/v1/auth/email",
            "/api/v1/auth/oauth2"
    );

    private final Key key;
    private final AccessTokenBlacklistService accessTokenBlacklistService;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret, AccessTokenBlacklistService accessTokenBlacklistService) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenBlacklistService = accessTokenBlacklistService;
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_EXACT_PATHS.contains(path)
                || PUBLIC_PATH_PREFIXES.stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isPublicPath(path)
                || (TELEGRAM_WEBHOOK_PATH.equals(path)
                && HttpMethod.POST.equals(exchange.getRequest().getMethod()))) {
            return chain.filter(exchange);
        }

        String accessToken = resolveAccessToken(exchange.getRequest());
        if(accessToken == null || accessToken.isBlank()){
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(accessToken).getBody();
            String tokenType = claims.get("tokenType", String.class);
            String tokenId = claims.getId();

            if(!"ACCESS".equals(tokenType) || tokenId == null || tokenId.isBlank()){
                return unauthorized(exchange);
            }

            return accessTokenBlacklistService.isBlacklisted(tokenId)
                    .flatMap(isBlacklisted -> {
                        if(Boolean.TRUE.equals(isBlacklisted)){
                            return unauthorized(exchange);
                        }
                        return forwardAuthenticatedRequest(exchange, chain, claims);
                    });
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange);
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange){
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> forwardAuthenticatedRequest(ServerWebExchange exchange, GatewayFilterChain chain, Claims claims){
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
