package site.yesaido.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouterLocateConfig {

    private static final String USER_LB_URL = "lb://user-server";
    private static final String CULTIVATION_LB_URL = "lb://cultivation-server";

    @Bean
    public RouteLocator myRoute(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-server",
                        p -> p.path("/api/users/**", "/api/auth/**")
                                .uri(USER_LB_URL))
                .route("cultivation-server",
                        p -> p.path("/api/cultivations/**")
                                .uri(CULTIVATION_LB_URL))
                .build();
    }
}