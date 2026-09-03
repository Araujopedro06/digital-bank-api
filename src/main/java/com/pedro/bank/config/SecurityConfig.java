package com.pedro.bank.config;

import com.pedro.bank.security.AppUserDetailsService;
import com.pedro.bank.security.JwtAuthFilter;
import com.pedro.bank.security.JwtProperties;
import com.pedro.bank.security.JwtService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtService jwtService, AppUserDetailsService userDetailsService,
                          @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * States the CORS policy in the startup log.
     *
     * <p>A misconfigured allow-list is close to invisible from the outside: the
     * request is refused by the CORS filter with a bare 403 before any controller
     * runs, and curl — which sends no Origin — reports the same endpoint as
     * perfectly healthy. Printing the effective list turns "the deployed site
     * cannot log in" into a question the deployment log answers directly.
     */
    @PostConstruct
    void reportCorsPolicy() {
        List<String> configured = allowedOrigins.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();

        if (configured.isEmpty()) {
            log.error("CORS allow-list is EMPTY — every browser request will be refused with 403. "
                    + "Set CORS_ORIGINS to the front end's exact origin, e.g. https://example.netlify.app");
        } else {
            log.info("CORS allow-list: {}", String.join(", ", configured));
        }
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .anyRequest().authenticated())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                // 401 rather than the default 403, so the SPA knows to send the user back to login.
                .exceptionHandling(handling -> handling.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new JwtAuthFilter(jwtService, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Patterns, not exact origins: testing on a phone means the origin is
        // whatever LAN address the machine happens to have today. Plain origins
        // are still valid patterns, so prod can keep listing them exactly.
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }
}
