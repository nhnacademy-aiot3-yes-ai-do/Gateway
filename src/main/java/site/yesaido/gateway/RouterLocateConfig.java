package site.yesaido.gateway;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(GatewayUpstreamProperties.class)
public class RouterLocateConfig {

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
                .build();
    }
}