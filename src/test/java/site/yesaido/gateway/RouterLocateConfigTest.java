package site.yesaido.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    @DisplayName("cultivation-server 라우트는 재배지 및 관리자 센서 API의 /api/v1 경로를 매칭한다")
    void cultivationServerRouteMatchesCultivationApiV1Paths() {
        Route route = findRoute("cultivation-server");

        assertThat(matches(route, "/api/v1/cultivations/1")).isTrue();
        assertThat(matches(route, "/api/v1/admin/mushroom-references")).isTrue();
        assertThat(matches(route, "/api/v1/admin/sensor-types")).isTrue();
        assertThat(matches(route, "/api/cultivations/1")).isFalse();
    }

    @Test
    @DisplayName("cultivation-server 라우트는 다른 경로를 매칭하지 않는다")
    void cultivationServerRouteDoesNotMatchOtherPaths() {
        Route route = findRoute("cultivation-server");

        assertThat(matches(route, "/api/users/1")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "user-server, 8081",
            "cultivation-server, 8084",
            "notification-server, 8085",
            "ai-server, 8000"
    })
    @DisplayName("라우트는 로컬 서비스의 명시 HTTP URL로 향한다")
    void routeTargetsConfiguredDirectHttpUrl(String routeId, int port) {
        Route route = findRoute(routeId);

        assertThat(route.getUri().getScheme()).isEqualTo("http");
        assertThat(route.getUri().getHost()).isEqualTo("localhost");
        assertThat(route.getUri().getPort()).isEqualTo(port);
    }

    @Test
    @DisplayName("notification-server 라우트는 Notification API 경로를 매칭한다")
    void notificationServerRouteMatchesNotificationPaths() {
        Route route = findRoute("notification-server");

        assertThat(matches(route, "/api/v1/notifications")).isTrue();
        assertThat(matches(route, "/api/v1/notification-endpoints")).isTrue();
        assertThat(matches(route, "/api/v1/notification-subscriptions/1/enabled")).isTrue();
        assertThat(matches(route, "/api/v1/notification-subscription-types")).isTrue();
    }

    @Test
    @DisplayName("notification-server 라우트는 다른 경로를 매칭하지 않는다")
    void notificationServerRouteDoesNotMatchOtherPaths() {
        Route route = findRoute("notification-server");

        assertThat(matches(route, "/api/users/1")).isFalse();
        assertThat(matches(route, "/api/v1/cultivations/1")).isFalse();
    }
}