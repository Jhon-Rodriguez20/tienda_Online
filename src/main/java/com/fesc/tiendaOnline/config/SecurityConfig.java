package com.fesc.tiendaOnline.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
    private final Environment environment;

    @Value("${app.cors.allowed-origins:http://localhost:4200/}")
    private List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitingFilter rateLimitingFilter,
                          UserDetailsService userDetailsService,
                          Environment environment) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.userDetailsService = userDetailsService;
        this.environment = environment;
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
                .requestMatchers(HttpMethod.POST, "/auth/refresh").permitAll()
                // Registro y verificación de cuenta
                .requestMatchers(HttpMethod.POST, "/usuario/registro").permitAll()
                .requestMatchers(HttpMethod.POST, "/usuario/verificar").permitAll()
                .requestMatchers(HttpMethod.POST, "/usuario/reenviar-codigo").permitAll()
                // Recuperación de contraseña
                .requestMatchers(HttpMethod.POST, "/usuario/recuperar/solicitar").permitAll()
                .requestMatchers(HttpMethod.POST, "/usuario/recuperar/verificar").permitAll()
                .requestMatchers(HttpMethod.POST, "/usuario/recuperar/cambiar-contrasena").permitAll()
                // Productos: lectura pública
                .requestMatchers(HttpMethod.GET, "/productos", "/productos/{idProducto}").permitAll()
                .requestMatchers(HttpMethod.GET, "/productos/buscar", "/productos/buscar/nombre").permitAll()
                .requestMatchers(HttpMethod.POST, "/productos/buscar/avanzado").permitAll()
                // Productos Categorias: público para lectura, admin para gestión
                .requestMatchers(HttpMethod.GET, "/productos/categorias/public").permitAll()
                // Productos Categorias: solo ADMIN
                .requestMatchers(HttpMethod.GET, "/productos/categorias").hasRole("ADMIN")
                // Archivos estáticos
                .requestMatchers("/uploads/**", "/images/**").permitAll()
                // Swagger / OpenAPI
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
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
                // Compras: consulta de estado de pago Wompi (autenticado)
                .requestMatchers(HttpMethod.GET, "/compras/{compraId}/pago/estado").authenticated()
                // Wompi webhook: público (verificación de firma se hace en el servicio)
                .requestMatchers(HttpMethod.POST, "/pagos/wompi/webhook").permitAll()
                // Reviews: rutas específicas antes de wildcards
                .requestMatchers(HttpMethod.GET, "/reviews/producto/*/mi-review").authenticated()
                // Reviews: lectura pública
                .requestMatchers(HttpMethod.GET, "/reviews/producto/**").permitAll()
                // Reviews: escritura solo CLIENTE
                .requestMatchers(HttpMethod.POST, "/reviews").hasRole("CLIENTE")
                .requestMatchers(HttpMethod.DELETE, "/reviews/{idReview}").hasRole("CLIENTE")
                // Perfil de usuario: autenticado
                .requestMatchers(HttpMethod.GET, "/usuario/perfil").authenticated()
                .requestMatchers(HttpMethod.PUT, "/usuario/perfil").authenticated()
                // Todo lo demás requiere autenticación
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            http.redirectToHttps(redirect -> redirect.requestMatchers(r -> true));
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // REDIRECCIONAR DE HTTP A HTTPS PARA PRODUCCION
        List<String> originsToUse = allowedOrigins;
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            originsToUse = allowedOrigins.stream()
                    .filter(origin -> origin.startsWith("https://"))
                    .collect(Collectors.toList());
        }

        configuration.setAllowedOrigins(originsToUse);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "Accept", "X-Requested-With",
            "Idempotency-Key", "X-Forwarded-For"
        ));
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization", "Idempotency-Replayed",
            "X-RateLimit-Remaining", "X-RateLimit-Limit", "Retry-After"
        ));
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
