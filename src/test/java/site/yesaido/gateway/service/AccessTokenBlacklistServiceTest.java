package site.yesaido.gateway.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccessTokenBlacklistServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Test
    @DisplayName("jti에 해당하는 Redis 블랙리스트 키를 조회한다")
    void isBlacklistedChecksTokenIdKey() {
        AccessTokenBlacklistService service = new AccessTokenBlacklistService(redisTemplate);
        given(redisTemplate.hasKey("AT:blacklist:token-id")).willReturn(Mono.just(true));

        Boolean result = service.isBlacklisted("token-id").block();

        assertThat(result).isTrue();
        verify(redisTemplate).hasKey("AT:blacklist:token-id");
    }
}
