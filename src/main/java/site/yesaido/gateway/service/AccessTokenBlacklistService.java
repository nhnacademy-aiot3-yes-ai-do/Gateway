package site.yesaido.gateway.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AccessTokenBlacklistService {
    private static final String BLACKLIST_KEY_PREFIX = "AT:blacklist:";
    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<Boolean> isBlacklisted(String tokenId){
        return redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + tokenId);
    }
}
