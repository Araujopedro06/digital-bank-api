package com.pedro.bank.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Accepts {@code DATABASE_URL} in the form managed hosts actually hand out.
 *
 * <p>Render, Fly and Heroku all expose a single connection string shaped like
 * {@code postgres://user:password@host:5432/dbname}, which JDBC cannot parse.
 * Pasting it straight into the dashboard is the obvious thing to do, and the
 * failure it produces ("Driver claims to not accept jdbcUrl") points nowhere
 * near the cause. This splits that form into the three properties Spring wants,
 * and leaves an already-JDBC URL untouched.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication app) {
        String url = environment.getProperty("DATABASE_URL");
        if (url == null || url.isBlank() || url.startsWith("jdbc:")) {
            return;
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            return; // Not a URL we understand; let the datasource fail with its own message.
        }

        Map<String, Object> resolved = new HashMap<>();
        resolved.put("spring.datasource.url", jdbcUrl(uri));

        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            String[] parts = userInfo.split(":", 2);
            resolved.put("spring.datasource.username", decode(parts[0]));
            if (parts.length > 1) {
                resolved.put("spring.datasource.password", decode(parts[1]));
            }
        }

        environment.getPropertySources()
                .addFirst(new MapPropertySource("database-url-from-connection-string", resolved));
    }

    private String jdbcUrl(URI uri) {
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() != -1) {
            jdbc.append(':').append(uri.getPort());
        }
        jdbc.append(uri.getPath());
        if (uri.getQuery() != null) {
            jdbc.append('?').append(uri.getQuery());
        }
        return jdbc.toString();
    }

    private String decode(String value) {
        return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
