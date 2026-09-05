
package com.retailpos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.net.URI;

@Configuration
@SuppressWarnings("null")
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${REDIS_URL:${spring.data.redis.url:}}")
    private String redisUrl;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        try {
            if (redisUrl != null && !redisUrl.trim().isEmpty()) {
                URI uri = URI.create(redisUrl.trim());
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 6379;
                RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);

                if (uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) {
                    String[] userParts = uri.getUserInfo().split(":", 2);
                    if (userParts.length == 2) {
                        if (!userParts[0].isEmpty())
                            config.setUsername(userParts[0]);
                        config.setPassword(RedisPassword.of(userParts[1]));
                    } else if (userParts.length == 1) {
                        config.setPassword(RedisPassword.of(userParts[0]));
                    }
                }

                LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfig = LettuceClientConfiguration
                        .builder();
                if ("rediss".equalsIgnoreCase(uri.getScheme())) {
                    clientConfig.useSsl();
                }
                log.info("Redis configured via REDIS_URL -> host: {}, port: {}, ssl: {}", host, port,
                        "rediss".equalsIgnoreCase(uri.getScheme()));
                return new LettuceConnectionFactory(config, clientConfig.build());
            }

            // Fallback to host/port/password
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
            if (redisPassword != null && !redisPassword.trim().isEmpty()) {
                config.setPassword(RedisPassword.of(redisPassword.trim()));
            }
            log.info("Redis configured via host/port -> host: {}, port: {}", redisHost, redisPort);
            return new LettuceConnectionFactory(config);
        } catch (Exception e) {
            log.warn("Failed to initialize RedisConnectionFactory: {}. Fallback to default localhost.", e.getMessage());
            return new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 6379));
        }
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
