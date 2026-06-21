package com.hotevent.cache;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.integration.redis.util.RedisLockRegistry;

import java.util.List;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        objectMapper.registerModule(new JavaTimeModule());

        Jackson2JsonRedisSerializer<Object> jacksonSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jacksonSerializer);
        template.setHashValueSerializer(jacksonSerializer);
        template.afterPropertiesSet();

        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisLockRegistry redisLockRegistry(RedisConnectionFactory connectionFactory) {
        return new RedisLockRegistry(connectionFactory, "hotevent:locks", 60000L);
    }

    @Bean
    public RedisScript<Boolean> bloomFilterAddScript() {
        DefaultRedisScript<Boolean> script = new DefaultRedisScript<>();
        script.setScriptText(
                "local key = KEYS[1]\n" +
                "local value = ARGV[1]\n" +
                "redis.call('SETBIT', key, redis.call('BITCOUNT', key) % 1000000, 1)\n" +
                "return redis.call('GETBIT', key, redis.call('BITCOUNT', key) % 1000000) == 1"
        );
        script.setResultType(Boolean.class);
        return script;
    }

    @Bean
    public RedisScript<Boolean> cacheCompareAndDeleteScript() {
        DefaultRedisScript<Boolean> script = new DefaultRedisScript<>();
        script.setScriptText(
                "local key = KEYS[1]\n" +
                "local expected = ARGV[1]\n" +
                "local current = redis.call('GET', key)\n" +
                "if current == expected then\n" +
                "    redis.call('DEL', key)\n" +
                "    return true\n" +
                "else\n" +
                "    return false\n" +
                "end"
        );
        script.setResultType(Boolean.class);
        return script;
    }
}
