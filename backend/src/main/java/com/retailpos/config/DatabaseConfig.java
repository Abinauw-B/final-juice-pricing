package com.retailpos.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${spring.datasource.url}")
    private String rawUrl;

    @Value("${spring.datasource.username:postgres}")
    private String rawUsername;

    @Value("${spring.datasource.password:postgres}")
    private String rawPassword;

    @Value("${spring.datasource.hikari.maximum-pool-size:100}")
    private int maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:20}")
    private int minIdle;

    @Bean
    @Primary
    public DataSource dataSource() {
        String url = rawUrl;
        String username = rawUsername;
        String password = rawPassword;

        // Auto-convert standard cloud PaaS URLs like postgres://user:pass@host:port/db to jdbc:postgresql://...
        if (url != null && (url.startsWith("postgres://") || url.startsWith("postgresql://"))) {
            try {
                String cleanUrl = url.replaceFirst("^postgres(ql)?://", "http://");
                URI uri = URI.create(cleanUrl);
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String path = uri.getPath(); // /dbname
                url = "jdbc:postgresql://" + host + ":" + port + path;

                if (uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) {
                    String[] userInfo = uri.getUserInfo().split(":", 2);
                    if (username == null || username.isEmpty() || "postgres".equals(username)) {
                        username = userInfo[0];
                    }
                    if ((password == null || password.isEmpty() || "postgres".equals(password)) && userInfo.length > 1) {
                        password = userInfo[1];
                    }
                }
                log.info("Converted cloud DATABASE_URL to JDBC URL: jdbc:postgresql://{}:{}{}", host, port, path);
            } catch (Exception e) {
                log.warn("Failed to parse cloud DATABASE_URL as URI, using raw URL: {}", e.getMessage());
            }
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(60000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        if (url != null && url.contains(":h2:")) {
            config.setDriverClassName("org.h2.Driver");
        } else {
            config.setDriverClassName("org.postgresql.Driver");
        }

        return new HikariDataSource(config);
    }
}
