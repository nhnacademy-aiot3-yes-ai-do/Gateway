package site.yesaido.gateway;

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

    private Route findBooksearchRoute() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        return routes.stream()
                .filter(route -> route.getId().equals("team3-booksearch"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("team3-booksearch route not found"));
    }

    @Test
    void booksearchRouteTargetsLoadBalancedUri() {
        Route route = findBooksearchRoute();

        assertThat(route.getUri().getScheme()).isEqualTo("lb");
        assertThat(route.getUri().getHost()).isEqualTo("team3-booksearch");
    }

    @Test
    void booksearchRouteMatchesBooksApiPath() {
        Route route = findBooksearchRoute();

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v2/books/1").build());

        assertThat(Mono.from(route.getPredicate().apply(exchange)).block()).isTrue();
    }

    @Test
    void booksearchRouteDoesNotMatchOtherPaths() {
        Route route = findBooksearchRoute();

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v2/orders/1").build());

        assertThat(Mono.from(route.getPredicate().apply(exchange)).block()).isFalse();
    }
}