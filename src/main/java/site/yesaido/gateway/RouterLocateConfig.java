package site.yesaido.gateway;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                                        "/api/users/**",
                                        "/api/auth/**",
                                        "/api/inquiries/**",
                                        "/api/admin/inquiries/**")
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
                                        "/api/v1/notification-subscription-types/**")
                                .uri(upstreamProperties.notificationUrl().toString()))
                .route("ai-server",
                        p -> p.path(
                                        "/api/mushrooms/**",
                                        "/api/ai/**",
                                        "/api/admin/data")
                                .uri(upstreamProperties.aiUrl().toString()))
                .build();
    }
}