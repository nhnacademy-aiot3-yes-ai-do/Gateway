package site.yesaido.gateway.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRequestResponseLoggingFilterTest {

    @Test
    void logsReceivedRequestAndResponseStatusWithoutQueryOrAuthorization() {
        GatewayRequestResponseLoggingFilter filter = new GatewayRequestResponseLoggingFilter();
        Logger logger = (Logger) LoggerFactory.getLogger(GatewayRequestResponseLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/cultivations/7?token=must-not-log")
                            .header("Authorization", "Bearer must-not-log")
                            .build());
            GatewayFilterChain chain = currentExchange -> {
                currentExchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return Mono.empty();
            };

            filter.filter(exchange, chain).block();

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .contains(
                            "gateway_request method=GET path=/api/v1/cultivations/7",
                            "gateway_response method=GET path=/api/v1/cultivations/7 status=403"
                    )
                    .noneMatch(message -> message.contains("must-not-log"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
