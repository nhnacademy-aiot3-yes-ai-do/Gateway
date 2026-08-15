package site.yesaido.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouterLocateConfig {

    private static final String USER_LB_URL = "lb://user-server";
    private static final String CULTIVATION_LB_URL = "lb://cultivation-server";
    private static final String NOTIFICATION_LB_URL = "lb://notification-server";

    @Bean
    public RouteLocator myRoute(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-server",
                        p -> p.path("/api/users/**", "/api/auth/**")
                                .uri(USER_LB_URL))
                .route("cultivation-server",
                        p -> p.path(
                                        "/api/cultivations/**",
                                        "/api/v1/mushroom-references/**",
                                        "/api/v1/sensor-types/**")
                                .uri(CULTIVATION_LB_URL))
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
                                .uri(NOTIFICATION_LB_URL))
                .build();
    }
}