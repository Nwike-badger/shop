package semicolon.africa.waylchub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import semicolon.africa.waylchub.repository.userRepository.UserRepository;
import semicolon.africa.waylchub.service.userService.CustomUserDetailsService;
import semicolon.africa.waylchub.service.userService.JwtService;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${cors.permitted-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public JwtAuthenticationFilter jwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        return new JwtAuthenticationFilter(jwtService, userRepository);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthFilter,
            AuthenticationProvider authenticationProvider,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        return http
                .cors(c -> c.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)

                // ── Security response headers ──────────────────────────────────────────
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .contentTypeOptions(c -> {})
                        .referrerPolicy(r -> r
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                )

                .authorizeHttpRequests(auth -> auth

                        // ── Fully public ────────────────────────────────────────────────
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // ── Public browsing ─────────────────────────────────────────────
                        // Public browsing — no token needed
                        .requestMatchers(
                                "/api/categories/**",
                                "/api/products/**",
                                "/api/v1/search**",             // Allow Smart Search
                                "/api/v1/recommendations/**",   // Allow Trending & For You
                                "/api/v1/track/**"
                        ).permitAll()

                        // ── Cart — guests send X-Guest-ID instead of JWT ────────────────
                        .requestMatchers("/api/v1/cart/**").permitAll()

                        // ── Monnify webhook — no JWT, must be open ──────────────────────
                        .requestMatchers("/api/v1/payments/webhook/**").permitAll()

                        // ── Payment callback polling ────────────────────────────────────
                        .requestMatchers("/api/v1/orders/verify/**").permitAll()

                        // ── Admin routes — JWT + ROLE_ADMIN required ────────────────────
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // ── Everything else needs a valid JWT ───────────────────────────
                        .anyRequest().authenticated()
                )

                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(
                Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(
                Arrays.asList("Authorization", "Content-Type", "X-Guest-ID","ngrok-skip-browser-warning", "X-Session-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider(CustomUserDetailsService userDetailsService) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}