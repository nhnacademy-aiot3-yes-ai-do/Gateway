package site.yesaido.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouterLocateConfig {

    private static final String BOOKSEARCH_LB_URL = "lb://team3-booksearch";

    @Bean
    public RouteLocator myRoute(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("team3-booksearch",
                        p -> p.path("/api/v2/books/**")
                                .uri(BOOKSEARCH_LB_URL))
                .build();
    }
}