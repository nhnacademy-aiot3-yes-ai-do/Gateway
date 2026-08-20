package site.yesaido.gateway.filter;

import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class RoleAuthorizationFilter implements GlobalFilter, Ordered {
    private static final List<String> ADMIN_PATHS = List.of("/api/admin", "/api/v1/admin");
    private static final String ADMIN_ROLE = "ADMIN";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (ADMIN_PATHS.stream().anyMatch(path::startsWith)) {
            String role = exchange.getRequest().getHeaders().getFirst("X-User-Role");
            if (!ADMIN_ROLE.equals(role)) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}