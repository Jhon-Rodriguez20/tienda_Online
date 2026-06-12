package com.fesc.tiendaOnline.controller;

import jakarta.validation.Validation;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import com.fesc.tiendaOnline.service.CompraService;
import com.fesc.tiendaOnline.service.WompiService;

/**
 * Tests unitarios para validaciones condicionales de Wompi en CompraController.
 *
 * Utiliza MockMvc standalone (sin contexto Spring completo) para evitar
 * dependencias de seguridad, JPA, etc. Las validaciones de Wompi ocurren
 * ANTES de la llamada a obtenerIdUsuarioAutenticado(), por lo que no se
 * requiere SecurityContext configurado.
 */
class CompraControllerWompiValidationTest {

    private MockMvc mockMvc;

    /** UUID v4 válido para el header Idempotency-Key en todos los tests. */
    private static final String VALID_IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";

    @BeforeEach
    void setUp() {
        CompraService compraService = Mockito.mock(CompraService.class);
        WompiService wompiService = Mockito.mock(WompiService.class);
        CompraController controller = new CompraController(compraService, wompiService);

        // MockMvc standalone: aplica Bean Validation pero omite Security y JPA.
        // SpringValidatorAdapter adapta jakarta.validation.Validator al tipo
        // org.springframework.validation.Validator que espera standaloneSetup.
        jakarta.validation.Validator jakartaValidator =
                Validation.buildDefaultValidatorFactory().getValidator();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new SpringValidatorAdapter(jakartaValidator))
                .build();
    }

    /**
     * Test 1: wompiTipoPago = "CARD" sin wompiCardToken → HTTP 400
     *
     * El controlador valida que cuando el tipo de pago es CARD, el token de
     * tarjeta es obligatorio. Esta validación ocurre antes de acceder al
     * SecurityContext, por lo que el test funciona sin autenticación.
     */
    @Test
    void testCardPayment_missingCardToken_returns400() throws Exception {
        String body = """
                {
                  "idMetodoPago": "550e8400-e29b-41d4-a716-446655440001",
                  "items": [{"idProducto": "550e8400-e29b-41d4-a716-446655440002", "cantidad": 1}],
                  "wompiTipoPago": "CARD"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/compras/realizar")
                        .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("El token de tarjeta es obligatorio para pagos con tarjeta")));
    }

    /**
     * Test 2: wompiTipoPago = "NEQUI" sin wompiNequiPhone → HTTP 400
     *
     * El controlador valida que cuando el tipo de pago es NEQUI, el número de
     * teléfono es obligatorio. Esta validación ocurre antes de acceder al
     * SecurityContext.
     */
    @Test
    void testNequiPayment_missingNequiPhone_returns400() throws Exception {
        String body = """
                {
                  "idMetodoPago": "550e8400-e29b-41d4-a716-446655440001",
                  "items": [{"idProducto": "550e8400-e29b-41d4-a716-446655440002", "cantidad": 1}],
                  "wompiTipoPago": "NEQUI"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/compras/realizar")
                        .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("El número de teléfono es obligatorio para pagos con Nequi")));
    }

    /**
     * Test 3: wompiTipoPago con valor no permitido → HTTP 400
     *
     * La anotación @Pattern en CompraRequestDTO rechaza valores que no sean
     * BANCOLOMBIA_TRANSFER, NEQUI o CARD. Spring lanza MethodArgumentNotValidException
     * que se traduce en HTTP 400 antes de llegar a la lógica del controlador.
     */
    @Test
    void testInvalidWompiTipoPago_patternViolation_returns400() throws Exception {
        String body = """
                {
                  "idMetodoPago": "550e8400-e29b-41d4-a716-446655440001",
                  "items": [{"idProducto": "550e8400-e29b-41d4-a716-446655440002", "cantidad": 1}],
                  "wompiTipoPago": "INVALID_TYPE"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/compras/realizar")
                        .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }
}
