package site.yesaido.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpMethod;
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
        return matches(route, HttpMethod.GET, path);
    }

    private boolean matches(Route route, HttpMethod method, String path) {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(method, path).build());
        return Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange)).block());
    }

    @Test
    @DisplayName("user-server 라우트는 /api/v1/users/**, /api/v1/auth/**, /api/v1/inquiries/** 경로를 매칭한다")
    void userServerRouteMatchesUsersAndAuthPaths() {
        Route route = findRoute("user-server");

        assertThat(matches(route, "/api/v1/users/check-email")).isTrue();
        assertThat(matches(route, "/api/v1/auth/login")).isTrue();
        assertThat(matches(route, "/api/v1/inquiries")).isTrue();
        assertThat(matches(route, "/api/v1/admin/inquiries")).isTrue();
        assertThat(matches(route, "/api/v1/admin/members")).isTrue();
    }

    @Test
    @DisplayName("user-server 라우트는 다른 경로를 매칭하지 않는다")
    void userServerRouteDoesNotMatchOtherPaths() {
        Route route = findRoute("user-server");

        assertThat(matches(route, "/api/v1/cultivations")).isFalse();
    }

    @Test
    @DisplayName("cultivation-server 라우트는 재배지 및 관리자 센서 API의 /api/v1 경로를 매칭한다")
    void cultivationServerRouteMatchesCultivationApiV1Paths() {
        Route route = findRoute("cultivation-server");

        assertThat(matches(route, "/api/v1/cultivations/1")).isTrue();
        assertThat(matches(route, "/api/v1/mushroom-references")).isTrue();
        assertThat(matches(route, "/api/v1/sensors/reusable")).isTrue();
        assertThat(matches(route, "/api/v1/sensor-types")).isTrue();
        assertThat(matches(route, "/api/v1/admin/mushroom-references")).isTrue();
        assertThat(matches(route, "/api/v1/admin/sensor-types")).isTrue();
        assertThat(matches(route, "/api/cultivations/1")).isFalse();
    }

    @Test
    @DisplayName("cultivation-server 라우트는 다른 경로를 매칭하지 않는다")
    void cultivationServerRouteDoesNotMatchOtherPaths() {
        Route route = findRoute("cultivation-server");

        assertThat(matches(route, "/api/v1/users/1")).isFalse();
    }

    @Test
    @DisplayName("cultivation-api-docs 라우트는 /v3/api-docs/cultivation 만 cultivation-server로 프록시한다")
    void cultivationApiDocsRouteMatchesOnlyItsExactPath() {
        Route route = findRoute("cultivation-api-docs");

        assertThat(matches(route, "/v3/api-docs/cultivation")).isTrue();
        assertThat(matches(route, "/v3/api-docs")).isFalse();
        assertThat(matches(route, "/v3/api-docs/swagger-config")).isFalse();
        assertThat(matches(route, "/v3/api-docs/user")).isFalse();
        assertThat(route.getUri().getPort()).isEqualTo(8084);
    }

    @Test
    @DisplayName("user-api-docs 라우트는 /v3/api-docs/user 만 user-server로 프록시한다")
    void userApiDocsRouteMatchesOnlyItsExactPath() {
        Route route = findRoute("user-api-docs");

        assertThat(matches(route, "/v3/api-docs/user")).isTrue();
        assertThat(matches(route, "/v3/api-docs")).isFalse();
        assertThat(matches(route, "/v3/api-docs/swagger-config")).isFalse();
        assertThat(matches(route, "/v3/api-docs/cultivation")).isFalse();
        assertThat(route.getUri().getPort()).isEqualTo(8081);
    }

    @Test
    @DisplayName("ai-api-docs 라우트는 /v3/api-docs/ai 만 ai-server로 프록시한다")
    void aiApiDocsRouteMatchesOnlyItsExactPath() {
        Route route = findRoute("ai-api-docs");

        assertThat(matches(route, "/v3/api-docs/ai")).isTrue();
        assertThat(matches(route, "/v3/api-docs")).isFalse();
        assertThat(matches(route, "/v3/api-docs/swagger-config")).isFalse();
        assertThat(matches(route, "/v3/api-docs/user")).isFalse();
        assertThat(route.getUri().getPort()).isEqualTo(8000);
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
        assertThat(matches(route, HttpMethod.POST, "/api/v1/telegram-link-sessions")).isTrue();
        assertThat(matches(route, HttpMethod.GET, "/api/v1/telegram-link-sessions/0d9cce63-4fcf-4c45-aa8a-f6a0adcf7d79"))
                .isTrue();
    }

    @Test
    @DisplayName("Telegram webhook route는 정확한 POST 요청만 Notification Server로 라우팅한다")
    void telegramWebhookRouteMatchesOnlyExactPostPath() {
        Route route = findRoute("telegram-webhook");

        assertThat(matches(route, HttpMethod.POST, "/webhooks/telegram")).isTrue();
        assertThat(matches(route, HttpMethod.GET, "/webhooks/telegram")).isFalse();
        assertThat(matches(route, HttpMethod.POST, "/webhooks/telegram/extra")).isFalse();
        assertThat(matches(route, HttpMethod.POST, "/internal/telegram/webhook")).isFalse();
    }

    @Test
    @DisplayName("notification-server 라우트는 다른 경로를 매칭하지 않는다")
    void notificationServerRouteDoesNotMatchOtherPaths() {
        Route route = findRoute("notification-server");

        assertThat(matches(route, "/api/v1/users/1")).isFalse();
        assertThat(matches(route, "/api/v1/cultivations/1")).isFalse();
    }

    @Test
    @DisplayName("ai-server 라우트는 AI 및 버섯 가이드, 관리자 데이터 API 경로를 매칭한다")
    void aiServerRouteMatchesAiAndMushroomPaths() {
        Route route = findRoute("ai-server");

        assertThat(matches(route, "/api/v1/ai/sensor/validate")).isTrue();
        assertThat(matches(route, "/api/v1/mushrooms/1/guide")).isTrue();
        assertThat(matches(route, "/api/v1/admin/data")).isTrue();
    }

    @Test
    @DisplayName("ai-server 라우트는 다른 서비스의 경로를 매칭하지 않는다")
    void aiServerRouteDoesNotMatchOtherPaths() {
        Route route = findRoute("ai-server");

        assertThat(matches(route, "/api/v1/users/1")).isFalse();
        assertThat(matches(route, "/api/v1/cultivations/1")).isFalse();
        assertThat(matches(route, "/api/v1/notifications")).isFalse();
    }
}
