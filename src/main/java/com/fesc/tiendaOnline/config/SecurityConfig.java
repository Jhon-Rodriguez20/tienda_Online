package com.fesc.tiendaOnline.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final UserDetailsService userDetailsService;

    @Value("${app.cors.allowed-origins:http://localhost:8080}")
    private List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitingFilter rateLimitingFilter,
                          UserDetailsService userDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )
            )
            .authorizeHttpRequests(auth -> auth
                // Auth
                .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                // Registro y verificación de cuenta
                .requestMatchers(HttpMethod.POST, "/usuario/registro").permitAll()
                .requestMatchers(HttpMethod.POST, "/usuario/verificar").permitAll()
                .requestMatchers(HttpMethod.POST, "/usuario/reenviar-codigo").permitAll()
                // Recuperación de contraseña
                .requestMatchers(HttpMethod.POST, "/usuario/recuperar/solicitar").permitAll()
                .requestMatchers(HttpMethod.POST, "/usuario/recuperar/verificar").permitAll()
                .requestMatchers(HttpMethod.POST, "/usuario/recuperar/cambiar-contrasena").permitAll()
                // Productos: lectura pública
                .requestMatchers(HttpMethod.GET, "/productos", "/productos/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/productos/buscar/avanzado").permitAll()
                .requestMatchers(HttpMethod.GET, "/productos/buscar", "/productos/buscar/nombre").permitAll()
                // Archivos estáticos
                .requestMatchers("/uploads/**", "/images/**").permitAll()
                // Productos: escritura solo ADMIN
                .requestMatchers(HttpMethod.POST, "/productos").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/productos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/productos/**").hasRole("ADMIN")
                // Compras: rutas admin
                .requestMatchers(HttpMethod.POST, "/compras/admin/todas").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/compras/admin/{compraId}/estado").hasRole("ADMIN")
                // Compras: rutas cliente
                .requestMatchers(HttpMethod.GET, "/compras/metodo/pago").hasRole("CLIENTE")
                .requestMatchers(HttpMethod.POST, "/compras/realizar").hasRole("CLIENTE")
                .requestMatchers(HttpMethod.POST, "/compras/mis-compras").hasRole("CLIENTE")
                .requestMatchers(HttpMethod.DELETE, "/compras/{compraId}/cancelar").hasRole("CLIENTE")
                // Todo lo demás requiere autenticación
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "Accept", "X-Requested-With"
        ));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());

        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
