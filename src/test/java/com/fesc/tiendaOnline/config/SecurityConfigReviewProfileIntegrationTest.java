package com.fesc.tiendaOnline.config;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.fesc.tiendaOnline.exception.GlobalExceptionHandler;
import com.fesc.tiendaOnline.model.dto.ReviewCreateDTO;
import com.fesc.tiendaOnline.model.dto.UsuarioUpdateDTO;

import jakarta.validation.Valid;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SecurityConfig rules covering review and profile endpoints.
 * Uses a minimal Spring context with only the security rules replicated from
 * production SecurityConfig (reviews + profile sections) and stub controllers.
 * No JWT filter, no rate-limiting filter, no database — pure security rule testing.
 *
 * Validates: Requirements 9.1, 9.2, 9.3, 9.5, 9.6
 */
@SpringBootTest(
        classes = SecurityConfigReviewProfileIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
class SecurityConfigReviewProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ─── Test-only configuration ─────────────────────────────────────────────────

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(GlobalExceptionHandler.class)
    static class TestConfig {

        /**
         * Mirrors the production SecurityConfig rules for reviews and profile
         * endpoints WITHOUT any custom JWT filter or rate-limiting filter.
         */
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((request, response, authException) -> {
                        response.setStatus(401);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"No autorizado\"}");
                    })
                )
                .authorizeHttpRequests(auth -> auth
                    // Reviews: specific routes before wildcards
                    .requestMatchers(HttpMethod.GET, "/reviews/producto/*/mi-review").authenticated()
                    // Reviews: public read
                    .requestMatchers(HttpMethod.GET, "/reviews/producto/**").permitAll()
                    // Reviews: write only CLIENTE
                    .requestMatchers(HttpMethod.POST, "/reviews").hasRole("CLIENTE")
                    .requestMatchers(HttpMethod.DELETE, "/reviews/{idReview}").hasRole("CLIENTE")
                    // Profile: authenticated
                    .requestMatchers(HttpMethod.GET, "/usuario/perfil").authenticated()
                    .requestMatchers(HttpMethod.PUT, "/usuario/perfil").authenticated()
                    // Everything else requires authentication
                    .anyRequest().authenticated()
                );
            return http.build();
        }

        /**
         * Stub controller that mimics ReviewController endpoints for security testing.
         * Returns simple 200/204 responses when reached (past security).
         */
        @RestController
        @RequestMapping("/reviews")
        static class StubReviewController {

            @PostMapping
            public String crearReview(@Valid @RequestBody ReviewCreateDTO dto) {
                return "\"ok\"";
            }

            @DeleteMapping("/{idReview}")
            public String eliminarReview(@PathVariable UUID idReview) {
                return "\"deleted\"";
            }

            @GetMapping("/producto/{idProducto}")
            public String listarReviews(@PathVariable UUID idProducto) {
                return "[]";
            }

            @GetMapping("/producto/{idProducto}/estadisticas")
            public String estadisticas(@PathVariable UUID idProducto) {
                return "{}";
            }

            @GetMapping("/producto/{idProducto}/mi-review")
            public String miReview(@PathVariable UUID idProducto) {
                return "{}";
            }
        }

        /**
         * Stub controller that mimics UsuarioController profile endpoints.
         */
        @RestController
        @RequestMapping("/usuario")
        static class StubUsuarioController {

            @GetMapping("/perfil")
            public String obtenerPerfil() {
                return "{}";
            }

            @PutMapping("/perfil")
            public String actualizarPerfil(@Valid @RequestBody UsuarioUpdateDTO dto) {
                return "{}";
            }
        }
    }

    // ─── Helper constants ────────────────────────────────────────────────────────

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();

    private static final String VALID_REVIEW_JSON = """
            {
                "idProducto": "%s",
                "estrellas": 4,
                "comentario": "Excelente producto, muy buena calidad"
            }
            """.formatted(UUID.randomUUID());

    private static final String VALID_PROFILE_JSON = """
            {
                "nombre": "Juan Carlos",
                "apellido": "Pérez López",
                "telefono": "3001234567",
                "pais": "Colombia",
                "departamento": "Cundinamarca",
                "ciudad": "Bogotá",
                "direccion": "Calle 123 #45-67 Apto 101",
                "codigoPostal": "110111"
            }
            """;

    // ═══════════════════════════════════════════════════════════════════════════════
    // PUBLIC ENDPOINTS — Accessible without authentication (Req 9.1)
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Public GET endpoints (no authentication required)")
    class PublicEndpoints {

        @Test
        @DisplayName("GET /reviews/producto/{id} — accessible without token → 200")
        void reviewsListAccessibleWithoutToken() throws Exception {
            mockMvc.perform(get("/reviews/producto/{idProducto}", PRODUCT_ID))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /reviews/producto/{id}/estadisticas — accessible without token → 200")
        void reviewsStatisticsAccessibleWithoutToken() throws Exception {
            mockMvc.perform(get("/reviews/producto/{idProducto}/estadisticas", PRODUCT_ID))
                    .andExpect(status().isOk());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // PROTECTED ENDPOINTS — Reject without token → 401 (Req 9.6)
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Protected endpoints reject requests without token → 401")
    class UnauthenticatedRejection {

        @Test
        @DisplayName("POST /reviews without token → 401")
        void postReviewWithoutToken_returns401() throws Exception {
            mockMvc.perform(post("/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REVIEW_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("DELETE /reviews/{id} without token → 401")
        void deleteReviewWithoutToken_returns401() throws Exception {
            mockMvc.perform(delete("/reviews/{idReview}", REVIEW_ID))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /usuario/perfil without token → 401")
        void getProfileWithoutToken_returns401() throws Exception {
            mockMvc.perform(get("/usuario/perfil"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PUT /usuario/perfil without token → 401")
        void putProfileWithoutToken_returns401() throws Exception {
            mockMvc.perform(put("/usuario/perfil")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_PROFILE_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /reviews/producto/{id}/mi-review without token → 401")
        void getMyReviewWithoutToken_returns401() throws Exception {
            mockMvc.perform(get("/reviews/producto/{idProducto}/mi-review", PRODUCT_ID))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // ADMIN ROLE — Rejected on review write endpoints → 403 (Req 9.5)
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ADMIN role rejected on POST/DELETE review endpoints → 403")
    class AdminRejection {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("POST /reviews as ADMIN → 403")
        void postReviewAsAdmin_returns403() throws Exception {
            mockMvc.perform(post("/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REVIEW_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("DELETE /reviews/{id} as ADMIN → 403")
        void deleteReviewAsAdmin_returns403() throws Exception {
            mockMvc.perform(delete("/reviews/{idReview}", REVIEW_ID))
                    .andExpect(status().isForbidden());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // CLIENTE ROLE — Can POST/DELETE reviews (Req 9.2)
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CLIENTE role can POST/DELETE reviews → 200/204")
    class ClienteAccess {

        @Test
        @WithMockUser(roles = "CLIENTE")
        @DisplayName("POST /reviews as CLIENTE → 200")
        void postReviewAsCliente_returns200() throws Exception {
            mockMvc.perform(post("/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REVIEW_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "CLIENTE")
        @DisplayName("DELETE /reviews/{id} as CLIENTE → 200")
        void deleteReviewAsCliente_returns200() throws Exception {
            mockMvc.perform(delete("/reviews/{idReview}", REVIEW_ID))
                    .andExpect(status().isOk());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // PROFILE REQUIRES AUTHENTICATION (Req 9.3)
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Profile endpoints require authentication")
    class ProfileAuth {

        @Test
        @WithMockUser(roles = "CLIENTE")
        @DisplayName("GET /usuario/perfil as authenticated → 200")
        void getProfileAuthenticated_returns200() throws Exception {
            mockMvc.perform(get("/usuario/perfil"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "CLIENTE")
        @DisplayName("PUT /usuario/perfil as authenticated → 200")
        void putProfileAuthenticated_returns200() throws Exception {
            mockMvc.perform(put("/usuario/perfil")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_PROFILE_JSON))
                    .andExpect(status().isOk());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // BEAN VALIDATION — Structured 400 errors
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Bean Validation returns structured 400 errors")
    class BeanValidation {

        @Test
        @WithMockUser(roles = "CLIENTE")
        @DisplayName("POST /reviews with invalid body → 400 with structured error")
        void postReviewInvalidBody_returns400WithStructuredError() throws Exception {
            String invalidReviewJson = """
                    {
                        "idProducto": null,
                        "estrellas": null,
                        "comentario": ""
                    }
                    """;

            mockMvc.perform(post("/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidReviewJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ERROR_VALIDACION"))
                    .andExpect(jsonPath("$.mensaje").value("Error de validación en los campos"))
                    .andExpect(jsonPath("$.errores").isMap())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @WithMockUser(roles = "CLIENTE")
        @DisplayName("PUT /usuario/perfil with invalid body → 400 with structured error")
        void putProfileInvalidBody_returns400WithStructuredError() throws Exception {
            String invalidProfileJson = """
                    {
                        "nombre": "",
                        "apellido": "",
                        "telefono": "abc",
                        "pais": "",
                        "departamento": "",
                        "ciudad": "",
                        "direccion": "short",
                        "codigoPostal": "123456789012345678"
                    }
                    """;

            mockMvc.perform(put("/usuario/perfil")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidProfileJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ERROR_VALIDACION"))
                    .andExpect(jsonPath("$.mensaje").value("Error de validación en los campos"))
                    .andExpect(jsonPath("$.errores").isMap())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }
}
