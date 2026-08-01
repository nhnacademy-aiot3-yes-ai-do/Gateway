package site.yesaido.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RouterLocateConfigTest {

    @Autowired
    private RouteLocator routeLocator;

    private Route findRoute(String routeId) {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        return routes.stream()
                .filter(route -> route.getId().equals(routeId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(routeId + " route not found"));
    }

    private boolean matches(Route route, String path) {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).build());
        return Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange)).block());
    }

    @Test
    @DisplayName("user-server 라우트는 lb://user-server로 향한다")
    void userServerRouteTargetsLoadBalancedUri() {
        Route route = findRoute("user-server");

        assertThat(route.getUri().getScheme()).isEqualTo("lb");
        assertThat(route.getUri().getHost()).isEqualTo("user-server");
    }

    @Test
    @DisplayName("user-server 라우트는 /api/users/**, /api/auth/** 경로를 매칭한다")
    void userServerRouteMatchesUsersAndAuthPaths() {
        Route route = findRoute("user-server");

        assertThat(matches(route, "/api/users/check-email")).isTrue();
        assertThat(matches(route, "/api/auth/login")).isTrue();
    }

    @Test
    @DisplayName("user-server 라우트는 다른 경로를 매칭하지 않는다")
    void userServerRouteDoesNotMatchOtherPaths() {
        Route route = findRoute("user-server");

        assertThat(matches(route, "/api/cultivations")).isFalse();
    }

    @Test
    @DisplayName("cultivation-server 라우트는 lb://cultivation-server로 향한다")
    void cultivationServerRouteTargetsLoadBalancedUri() {
        Route route = findRoute("cultivation-server");

        assertThat(route.getUri().getScheme()).isEqualTo("lb");
        assertThat(route.getUri().getHost()).isEqualTo("cultivation-server");
    }

    @Test
    @DisplayName("cultivation-server 라우트는 /api/cultivations/** 경로를 매칭한다")
    void cultivationServerRouteMatchesCultivationsPath() {
        Route route = findRoute("cultivation-server");

        assertThat(matches(route, "/api/cultivations/1")).isTrue();
    }

    @Test
    @DisplayName("cultivation-server 라우트는 다른 경로를 매칭하지 않는다")
    void cultivationServerRouteDoesNotMatchOtherPaths() {
        Route route = findRoute("cultivation-server");

        assertThat(matches(route, "/api/users/1")).isFalse();
    }
}