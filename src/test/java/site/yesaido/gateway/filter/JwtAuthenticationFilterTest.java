package site.yesaido.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-jwt-secret-key-for-unit-tests-please-ignore-1234567890";

    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(SECRET);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private String validToken(String subject) {
        return Jwts.builder()
                .setSubject(subject)
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    private String expiredToken(String subject) {
        return Jwts.builder()
                .setSubject(subject)
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    @DisplayName("공개 경로(로그인)는 토큰 없이도 통과")
    void publicPathBypassesAuthentication() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("공개 경로(회원가입)는 토큰 없이도 통과")
    void publicSignupPathBypassesAuthentication() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/users/signup").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("공개 경로(이메일 인증 발송/검증)는 토큰 없이도 통과")
    void publicEmailAuthPathsBypassAuthentication() {
        ServerWebExchange sendExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/email/send").build());
        filter.filter(sendExchange, chain).block();
        verify(chain).filter(sendExchange);

        reset(chain);
        when(chain.filter(any())).thenReturn(Mono.empty());

        ServerWebExchange verifyExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/email/verify").build());
        filter.filter(verifyExchange, chain).block();
        verify(chain).filter(verifyExchange);
    }

    @Test
    @DisplayName("Authorization 헤더 없으면 401")
    void missingAuthorizationHeaderReturns401() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/cultivations").build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Bearer로 시작하지 않는 Authorization 헤더면 401")
    void nonBearerAuthorizationHeaderReturns401() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/cultivations")
                        .header("Authorization", "Basic dXNlcjpwYXNz")
                        .build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("유효한 JWT면 X-User-Id 헤더를 추가해서 다음 필터로 통과")
    void validTokenAddsUserIdHeaderAndProceeds() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/cultivations")
                        .header("Authorization", "Bearer " + validToken("42"))
                        .build());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("42");
    }

    @Test
    @DisplayName("만료된 JWT면 401")
    void expiredTokenReturns401() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/cultivations")
                        .header("Authorization", "Bearer " + expiredToken("42"))
                        .build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("서명이 다른(위조된) JWT면 401")
    void tamperedTokenReturns401() {
        String wrongSecret = "completely-different-secret-key-value-1234567890ab";
        String tamperedToken = Jwts.builder()
                .setSubject("42")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(wrongSecret.getBytes()), SignatureAlgorithm.HS256)
                .compact();

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/cultivations")
                        .header("Authorization", "Bearer " + tamperedToken)
                        .build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("클라이언트가 보낸 X-User-Id는 JWT 사용자 ID로 덮어쓴다")
    void clientUserIdHeaderIsReplacedWithJwtSubject() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/inquiries/1")
                        .header("Authorization", "Bearer " + validToken("42"))
                        .header("X-User-Id", "999")
                        .build()
        );

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor =
                ArgumentCaptor.forClass(ServerWebExchange.class);

        verify(chain).filter(captor.capture());

        assertThat(captor.getValue().getRequest()
                .getHeaders()
                .get("X-User-Id"))
                .containsExactly("42");
    }

    @Test
    @DisplayName("필터 순서는 -2")
    void filterOrderIsMinusTwo() {
        assertThat(filter.getOrder()).isEqualTo(-2);
    }
}