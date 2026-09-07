package site.yesaido.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import site.yesaido.gateway.service.AccessTokenBlacklistService;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-jwt-secret-key-for-unit-tests-please-ignore-1234567890";

    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;
    private AccessTokenBlacklistService accessTokenBlacklistService;

    @BeforeEach
    void setUp() {
        accessTokenBlacklistService = mock(AccessTokenBlacklistService.class);
        when(accessTokenBlacklistService.isBlacklisted(anyString())).thenReturn(Mono.just(false));
        filter = new JwtAuthenticationFilter(SECRET, accessTokenBlacklistService);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private String validToken(String subject) {
        return validToken(subject, "token-id-" + subject);
    }

    private String validToken(String subject, String tokenId) {
        return Jwts.builder()
                .setId(tokenId)
                .setSubject(subject)
                .claim("tokenType", "ACCESS")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    private String expiredToken(String subject) {
        return Jwts.builder()
                .setId("expired-token-id")
                .setSubject(subject)
                .claim("tokenType", "ACCESS")
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    @DisplayName("공개 경로(로그인)는 토큰 없이도 통과")
    void publicPathBypassesAuthentication() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("공개 leaf 경로의 접두사 확장은 JWT 없이 접근할 수 없다")
    void publicLeafPathPrefixExtensionRequiresAuthentication() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/loginX").build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("공개 하위 경로의 경계 밖 접두사는 JWT 없이 접근할 수 없다")
    void publicChildPathPrefixExtensionRequiresAuthentication() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/emailX").build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("공개 경로(회원가입)는 토큰 없이도 통과")
    void publicSignupPathBypassesAuthentication() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/users/signup").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("공개 경로(회원가입 이메일 인증 확인)는 토큰 없이도 통과")
    void publicSignupEmailVerificationPathBypassesAuthentication() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/users/signup/verify-email").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("공개 경로(이메일 인증 발송/검증)는 토큰 없이도 통과")
    void publicEmailAuthPathsBypassAuthentication() {
        ServerWebExchange sendExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/email/send").build());
        filter.filter(sendExchange, chain).block();
        verify(chain).filter(sendExchange);

        reset(chain);
        when(chain.filter(any())).thenReturn(Mono.empty());

        ServerWebExchange verifyExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/email/verify").build());
        filter.filter(verifyExchange, chain).block();
        verify(chain).filter(verifyExchange);
    }

    @Test
    @DisplayName("API 문서 경로(Swagger UI / OpenAPI 스펙)는 토큰 없이도 통과")
    void swaggerAndApiDocsPathsBypassAuthentication() {
        for (String path : java.util.List.of(
                "/swagger-ui.html",
                "/swagger-ui/index.html",
                "/v3/api-docs/swagger-config",
                "/v3/api-docs/cultivation")) {
            reset(chain);
            when(chain.filter(any())).thenReturn(Mono.empty());

            ServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get(path).build());
            filter.filter(exchange, chain).block();

            verify(chain).filter(exchange);
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }

    @Test
    @DisplayName("Telegram webhook 공개 경로는 JWT 없이도 다음 필터로 통과")
    void telegramWebhookBypassesAuthenticationOnlyForExactPath() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/webhooks/telegram").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Telegram webhook GET 요청은 JWT 없이 접근할 수 없다")
    void telegramWebhookGetRequiresAuthentication() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/webhooks/telegram").build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Telegram webhook 하위 경로와 이전 내부 경로는 JWT 없이 접근할 수 없다")
    void telegramWebhookNonPublicPathsRequireAuthentication() {
        ServerWebExchange subpathExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/webhooks/telegram/extra").build());

        filter.filter(subpathExchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(subpathExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        reset(chain);
        when(chain.filter(any())).thenReturn(Mono.empty());

        ServerWebExchange legacyExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/internal/telegram/webhook").build());

        filter.filter(legacyExchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(legacyExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Authorization 헤더 없으면 401")
    void missingAuthorizationHeaderReturns401() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cultivations").build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Bearer로 시작하지 않는 Authorization 헤더면 401")
    void nonBearerAuthorizationHeaderReturns401() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cultivations")
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
                MockServerHttpRequest.get("/api/v1/cultivations")
                        .header("Authorization", "Bearer " + validToken("42"))
                        .build());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("42");
        verify(accessTokenBlacklistService).isBlacklisted("token-id-42");
    }

    @Test
    @DisplayName("블랙리스트에 등록된 Access Token은 401로 차단한다")
    void blacklistedTokenReturnsUnauthorized() {
        when(accessTokenBlacklistService.isBlacklisted("blocked-token-id")).thenReturn(Mono.just(true));
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cultivations")
                        .header("Authorization", "Bearer " + validToken("42", "blocked-token-id"))
                        .build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("ACCESS 타입 또는 jti가 없는 JWT는 인증에 사용할 수 없다")
    void tokenWithoutAccessTypeOrTokenIdReturnsUnauthorized() {
        String tokenWithoutAccessType = Jwts.builder()
                .setId("token-id")
                .setSubject("42")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cultivations")
                        .header("Authorization", "Bearer " + tokenWithoutAccessType)
                        .build());

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        verify(accessTokenBlacklistService, never()).isBlacklisted(anyString());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String tokenWithoutTokenId = Jwts.builder()
                .setSubject("42")
                .claim("tokenType", "ACCESS")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();
        ServerWebExchange noTokenIdExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cultivations")
                        .header("Authorization", "Bearer " + tokenWithoutTokenId)
                        .build());

        filter.filter(noTokenIdExchange, chain).block();

        assertThat(noTokenIdExchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("만료된 JWT면 401")
    void expiredTokenReturns401() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cultivations")
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
                MockServerHttpRequest.get("/api/v1/inquiries/1")
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

    private String validTokenWithRole(String subject, String role) {
        return Jwts.builder()
                .setId("role-token-id-" + subject)
                .setSubject(subject)
                .claim("role", role)
                .claim("tokenType", "ACCESS")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    @DisplayName("role 클레임이 있는 JWT면 X-User-Role 헤더도 추가해서 통과")
    void validTokenWithRoleAddsUserRoleHeader() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/admin/inquiries")
                        .header("Authorization", "Bearer " + validTokenWithRole("42", "ADMIN"))
                        .build());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("42");
        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("role 클레임이 없으면 클라이언트가 보낸 X-User-Role 헤더도 제거한다")
    void clientSpoofedRoleHeaderIsStrippedWhenJwtHasNoRole() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cultivations")
                        .header("Authorization", "Bearer " + validToken("42"))
                        .header("X-User-Role", "ADMIN")
                        .build());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders().get("X-User-Role")).isNull();
    }
    @Test
    @DisplayName("accessToken 쿠키의 유효한 JWT면 X-User-Id 헤더를 추가해서 다음 필터로 통과")
    void validAccessTokenCookieAddsUserIdHeaderAndProceeds() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cultivations")
                        .cookie(new HttpCookie("accessToken", validToken("42")))
                        .build()
        );

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor =
                ArgumentCaptor.forClass(ServerWebExchange.class);

        verify(chain).filter(captor.capture());

        assertThat(captor.getValue()
                .getRequest()
                .getHeaders()
                .getFirst("X-User-Id"))
                .isEqualTo("42");
    }

    @Test
    @DisplayName("로그아웃 경로는 Access Token 없이도 Auth 서버로 통과한다")
    void logoutPathBypassesAuthentication() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/logout").build()
        );

        filter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
        verify(accessTokenBlacklistService, never()).isBlacklisted(anyString());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
