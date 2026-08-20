package site.yesaido.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "gateway.upstream")
public record GatewayUpstreamProperties(
        URI userUrl,
        URI cultivationUrl,
        URI notificationUrl,
        URI aiUrl
) {
}
