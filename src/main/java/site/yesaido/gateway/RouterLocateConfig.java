package site.yesaido.gateway;

import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.Buildable;
import org.springframework.cloud.gateway.route.builder.PredicateSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(GatewayUpstreamProperties.class)
public class RouterLocateConfig {

    /** 각 서비스가 springdoc으로 OpenAPI 스펙을 노출하는 경로. */
    private static final String API_DOCS_PATH = "/v3/api-docs";

    private final GatewayUpstreamProperties upstreamProperties;

    @Bean
    public RouteLocator myRoute(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-server",
                        p -> p.path(
                                        "/api/v1/users/**",
                                        "/api/v1/auth/**",
                                        "/api/v1/inquiries/**",
                                        "/api/v1/admin/inquiries/**",
                                        "/api/v1/admin/members/**")
                                .uri(upstreamProperties.userUrl().toString()))
                .route("cultivation-server",
                        p -> p.path(
                                        "/api/v1/cultivations/**",
                                        "/api/v1/mushroom-references/**",
                                        "/api/v1/sensors/**",
                                        "/api/v1/sensor-types/**",
                                        "/api/v1/admin/mushroom-references/**",
                                        "/api/v1/admin/sensor-types/**")
                                .uri(upstreamProperties.cultivationUrl().toString()))
                .route("notification-server",
                        p -> p.path(
                                        "/api/v1/notifications",
                                        "/api/v1/notifications/**",
                                        "/api/v1/notification-endpoints",
                                        "/api/v1/notification-endpoints/**",
                                        "/api/v1/notification-subscriptions",
                                        "/api/v1/notification-subscriptions/**",
                                        "/api/v1/notification-subscription-types",
                                        "/api/v1/notification-subscription-types/**",
                                        "/api/v1/admin/notification-event-types",
                                        "/api/v1/admin/notification-event-types/**",
                                        "/api/v1/admin/notification-templates",
                                        "/api/v1/admin/notification-templates/**",
                                        "/api/v1/admin/channel-types",
                                        "/api/v1/admin/channel-types/**",
                                        "/api/v1/telegram-link-sessions",
                                        "/api/v1/telegram-link-sessions/**")
                                .uri(upstreamProperties.notificationUrl().toString()))
                .route("telegram-webhook",
                        p -> p.path("/webhooks/telegram")
                                .and().method(HttpMethod.POST)
                                .uri(upstreamProperties.notificationUrl().toString()))
                .route("ai-server",
                        p -> p.path(
                                        "/api/v1/ai/**",
                                        "/api/v1/mushrooms/**",
                                        "/api/v1/admin/data")
                                .uri(upstreamProperties.aiUrl().toString()))
                // API 문서: Swagger UI 자체는 Gateway가 로컬로 서빙하고,
                // 각 서비스의 OpenAPI 스펙만 <API_DOCS_PATH>/<service> -> 서비스의 <API_DOCS_PATH> 로 프록시한다.
                .route("user-api-docs", apiDocsRoute("user", upstreamProperties.userUrl().toString()))
                .route("cultivation-api-docs", apiDocsRoute("cultivation", upstreamProperties.cultivationUrl().toString()))
                .route("ai-api-docs", apiDocsRoute("ai", upstreamProperties.aiUrl().toString()))
                .route("notification-api-docs", apiDocsRoute("notification", upstreamProperties.notificationUrl().toString()))
                .build();
    }

    /**
     * {@code <API_DOCS_PATH>/<service>} 요청을 해당 서비스의 {@code <API_DOCS_PATH>} 로 프록시하는 라우트를 만든다.
     */
    private Function<PredicateSpec, Buildable<Route>> apiDocsRoute(String service, String upstreamUri) {
        String externalPath = API_DOCS_PATH + "/" + service;
        return p -> p.path(externalPath)
                .filters(f -> f.rewritePath(externalPath, API_DOCS_PATH))
                .uri(upstreamUri);
    }
}
