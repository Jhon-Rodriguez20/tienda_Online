// package com.fesc.tiendaOnline.service;

// import com.fesc.tiendaOnline.config.WompiConfig;
// import com.fesc.tiendaOnline.exception.WompiTimeoutException;
// import com.fesc.tiendaOnline.model.dto.WompiTransaccionRequestDTO;
// import com.fesc.tiendaOnline.model.dto.WompiTransaccionResponseDTO;
// import com.fesc.tiendaOnline.repository.CompraRepository;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.mockito.ArgumentCaptor;
// import org.mockito.Mockito;
// import org.mockito.stubbing.Answer;
// import org.springframework.http.MediaType;
// import org.springframework.web.client.ResourceAccessException;
// import org.springframework.web.client.RestClient;

// import java.nio.charset.StandardCharsets;
// import java.security.MessageDigest;
// import java.security.NoSuchAlgorithmException;

// import static org.assertj.core.api.Assertions.assertThat;
// import static org.assertj.core.api.Assertions.assertThatThrownBy;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.anyString;
// import static org.mockito.Mockito.when;

// /**
//  * Unit tests for {@link WompiService}.
//  *
//  * Uses manually wired RestClient mock stubs. The {@code header(String, String...)}
//  * method is a varargs method, so we stub it with {@code any(String[].class)} for
//  * the varargs parameter to avoid Mockito matcher mismatches.
//  */
// class WompiServiceTest {

//     private static final String BASE_URL      = "https://sandbox.wompi.co/v1";
//     private static final String PRIVATE_KEY   = "prv_staging_test";
//     private static final String INTEGRITY_KEY = "testIntegrityKey";

//     private WompiConfig      mockConfig;
//     private CompraRepository mockCompraRepo;

//     private RestClient                    mockRestClient;
//     private RestClient.RequestBodyUriSpec mockPostSpec;
//     private RestClient.RequestBodySpec    mockRequestBodySpec;
//     private RestClient.ResponseSpec       mockResponseSpec;

//     @BeforeEach
//     void setUp() {
//         mockConfig = Mockito.mock(WompiConfig.class);
//         when(mockConfig.getBaseUrl()).thenReturn(BASE_URL);
//         when(mockConfig.getPrivateKey()).thenReturn(PRIVATE_KEY);
//         when(mockConfig.getIntegrityKey()).thenReturn(INTEGRITY_KEY);

//         mockCompraRepo = Mockito.mock(CompraRepository.class);

//         mockRestClient      = Mockito.mock(RestClient.class);
//         mockPostSpec        = Mockito.mock(RestClient.RequestBodyUriSpec.class);
//         mockRequestBodySpec = Mockito.mock(RestClient.RequestBodySpec.class);
//         mockResponseSpec    = Mockito.mock(RestClient.ResponseSpec.class);

//         // Wire the fluent chain step by step.
//         // header() is declared as header(String name, String... values) — use
//         // any(String[].class) for the varargs parameter.
//         when(mockRestClient.post()).thenReturn(mockPostSpec);
//         when(mockPostSpec.uri(anyString())).thenReturn(mockRequestBodySpec);
//         when(mockRequestBodySpec.header(anyString(), any(String[].class))).thenReturn(mockRequestBodySpec);
//         when(mockRequestBodySpec.contentType(any(MediaType.class))).thenReturn(mockRequestBodySpec);
//         when(mockRequestBodySpec.body(any(Object.class))).thenReturn(mockRequestBodySpec);
//         when(mockRequestBodySpec.retrieve()).thenReturn(mockResponseSpec);
//         when(mockResponseSpec.onStatus(any(), any())).thenReturn(mockResponseSpec);
//     }

//     // -----------------------------------------------------------------------
//     // Test 1 — crearTransaccion con BANCOLOMBIA_TRANSFER
//     // -----------------------------------------------------------------------

//     /**
//      * Verifies that {@code crearTransaccion} with a BANCOLOMBIA_TRANSFER payment method
//      * returns the response provided by the mock, including the {@code async_payment_url}.
//      */
//     @Test
//     void crearTransaccion_bancolombiaTransfer_returnsResponseWithAsyncPaymentUrl() {
//         WompiTransaccionResponseDTO mockResponse =
//                 buildResponse("txn_bc_001", "PENDING", "https://checkout.wompi.co/l/test_bc");

//         when(mockResponseSpec.body(WompiTransaccionResponseDTO.class)).thenReturn(mockResponse);

//         WompiService service = new WompiService(mockConfig, mockCompraRepo, mockRestClient);

//         WompiTransaccionRequestDTO request =
//                 buildRequest("BANCOLOMBIA_TRANSFER", null, null, null);
//         WompiTransaccionResponseDTO result = service.crearTransaccion(request);

//         assertThat(result).isNotNull();
//         assertThat(result.getId()).isEqualTo("txn_bc_001");
//         assertThat(result.getStatus()).isEqualTo("PENDING");
//         assertThat(result.getAsync_payment_url())
//                 .as("BANCOLOMBIA_TRANSFER should return an async_payment_url")
//                 .isNotNull()
//                 .isNotBlank()
//                 .isEqualTo("https://checkout.wompi.co/l/test_bc");
//     }

//     // -----------------------------------------------------------------------
//     // Test 2 — crearTransaccion con NEQUI
//     // -----------------------------------------------------------------------

//     /**
//      * Verifies that {@code crearTransaccion} with a NEQUI payment method correctly
//      * carries the phone number in the request DTO passed to the HTTP call.
//      */
//     @Test
//     void crearTransaccion_nequi_requestContainsPhoneNumber() {
//         WompiTransaccionResponseDTO mockResponse =
//                 buildResponse("txn_nq_001", "PENDING", null);

//         // Override the body() stub with a captor so we can inspect what was passed
//         ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
//         when(mockRequestBodySpec.body(bodyCaptor.capture())).thenReturn(mockRequestBodySpec);

//         when(mockResponseSpec.body(WompiTransaccionResponseDTO.class)).thenReturn(mockResponse);

//         WompiService service = new WompiService(mockConfig, mockCompraRepo, mockRestClient);

//         String expectedPhone = "3001234567";
//         WompiTransaccionRequestDTO request =
//                 buildRequest("NEQUI", expectedPhone, null, null);

//         WompiTransaccionResponseDTO result = service.crearTransaccion(request);

//         assertThat(result).isNotNull();
//         assertThat(result.getId()).isEqualTo("txn_nq_001");

//         Object captured = bodyCaptor.getValue();
//         assertThat(captured).isInstanceOf(WompiTransaccionRequestDTO.class);
//         WompiTransaccionRequestDTO capturedReq = (WompiTransaccionRequestDTO) captured;
//         assertThat(capturedReq.getPayment_method().getType()).isEqualTo("NEQUI");
//         assertThat(capturedReq.getPayment_method().getPhone_number())
//                 .as("NEQUI payment must include the phone_number")
//                 .isEqualTo(expectedPhone);
//     }

//     // -----------------------------------------------------------------------
//     // Test 3 — crearTransaccion con CARD
//     // -----------------------------------------------------------------------

//     /**
//      * Verifies that {@code crearTransaccion} with a CARD payment method carries the
//      * card token and installments, and contains NO raw card number.
//      */
//     @Test
//     void crearTransaccion_card_requestContainsTokenAndInstallmentsWithoutRawCardNumber() {
//         WompiTransaccionResponseDTO mockResponse =
//                 buildResponse("txn_card_001", "APPROVED", null);

//         ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
//         when(mockRequestBodySpec.body(bodyCaptor.capture())).thenReturn(mockRequestBodySpec);

//         when(mockResponseSpec.body(WompiTransaccionResponseDTO.class)).thenReturn(mockResponse);

//         WompiService service = new WompiService(mockConfig, mockCompraRepo, mockRestClient);

//         String expectedToken = "tok_staging_abc123";
//         WompiTransaccionRequestDTO request =
//                 buildRequest("CARD", null, expectedToken, 3);

//         WompiTransaccionResponseDTO result = service.crearTransaccion(request);

//         assertThat(result).isNotNull();
//         assertThat(result.getId()).isEqualTo("txn_card_001");

//         Object captured = bodyCaptor.getValue();
//         assertThat(captured).isInstanceOf(WompiTransaccionRequestDTO.class);
//         WompiTransaccionRequestDTO capturedReq = (WompiTransaccionRequestDTO) captured;

//         WompiTransaccionRequestDTO.PaymentMethod pm = capturedReq.getPayment_method();
//         assertThat(pm.getType()).isEqualTo("CARD");
//         assertThat(pm.getToken())
//                 .as("CARD payment must include the tokenized card token")
//                 .isEqualTo(expectedToken);
//         assertThat(pm.getInstallments())
//                 .as("CARD payment must include installments = 3")
//                 .isEqualTo(3);
//         assertThat(pm.getPhone_number())
//                 .as("CARD payment must NOT contain a raw phone_number")
//                 .isNull();
//     }

//     // -----------------------------------------------------------------------
//     // Test 4 — calcularFirmaIntegridad
//     // -----------------------------------------------------------------------

//     /**
//      * Verifies that {@code calcularFirmaIntegridad} produces the correct SHA-256 hex
//      * for known inputs.
//      *
//      * Expected concatenation: {@code "REF001" + 5000L + "COP" + "testIntegrityKey"}
//      * → {@code "REF0015000COPtestIntegrityKey"}
//      */
//     @Test
//     void calcularFirmaIntegridad_knownInputs_matchesExpectedSha256() throws NoSuchAlgorithmException {
//         WompiService service = new WompiService(mockConfig, mockCompraRepo, mockRestClient);

//         String expected = sha256Hex("REF0015000COPtestIntegrityKey");
//         String result   = service.calcularFirmaIntegridad("REF001", 5000L, "COP");

//         assertThat(result)
//                 .as("SHA-256 of 'REF0015000COPtestIntegrityKey' must match")
//                 .isEqualTo(expected);
//     }

//     // -----------------------------------------------------------------------
//     // Test 5 — Timeout → WompiTimeoutException
//     // -----------------------------------------------------------------------

//     /**
//      * Verifies that when the HTTP call throws {@link ResourceAccessException} (socket
//      * timeout), {@code crearTransaccion} wraps it in a {@link WompiTimeoutException}.
//      */
//     @Test
//     void crearTransaccion_resourceAccessException_throwsWompiTimeoutException() {
//         when(mockRequestBodySpec.retrieve())
//                 .thenThrow(new ResourceAccessException("Connection timed out"));

//         WompiService service = new WompiService(mockConfig, mockCompraRepo, mockRestClient);
//         WompiTransaccionRequestDTO request =
//                 buildRequest("BANCOLOMBIA_TRANSFER", null, null, null);

//         assertThatThrownBy(() -> service.crearTransaccion(request))
//                 .isInstanceOf(WompiTimeoutException.class)
//                 .hasMessageContaining("Timeout al crear transacción con Wompi");
//     }

//     // -----------------------------------------------------------------------
//     // Helpers
//     // -----------------------------------------------------------------------

//     private WompiTransaccionResponseDTO buildResponse(String id, String status, String asyncUrl) {
//         WompiTransaccionResponseDTO dto = new WompiTransaccionResponseDTO();
//         dto.setId(id);
//         dto.setStatus(status);
//         dto.setAsync_payment_url(asyncUrl);
//         return dto;
//     }

//     /**
//      * Builds a {@link WompiTransaccionRequestDTO} for the given payment method parameters.
//      *
//      * @param type         payment method type (BANCOLOMBIA_TRANSFER, NEQUI, CARD)
//      * @param phoneNumber  phone number (for NEQUI); {@code null} otherwise
//      * @param token        card token (for CARD); {@code null} otherwise
//      * @param installments number of installments (for CARD); {@code null} defaults to 1
//      */
//     private WompiTransaccionRequestDTO buildRequest(String type, String phoneNumber,
//                                                      String token, Integer installments) {
//         WompiTransaccionRequestDTO dto = new WompiTransaccionRequestDTO();
//         dto.setAmount_in_cents(50000L);
//         dto.setCurrency("COP");
//         dto.setCustomer_email("user@example.com");
//         dto.setReference("REF001");

//         WompiTransaccionRequestDTO.PaymentMethod pm = new WompiTransaccionRequestDTO.PaymentMethod();
//         pm.setType(type);
//         pm.setPhone_number(phoneNumber);
//         pm.setToken(token);
//         if (installments != null) {
//             pm.setInstallments(installments);
//         }
//         dto.setPayment_method(pm);

//         WompiTransaccionRequestDTO.Signature sig = new WompiTransaccionRequestDTO.Signature();
//         sig.setIntegrity("dummy_signature");
//         dto.setSignature(sig);

//         return dto;
//     }

//     /**
//      * Computes SHA-256 hex digest for the given data string (UTF-8 encoded).
//      * Mirrors the algorithm used by {@link WompiService#calcularFirmaIntegridad}.
//      */
//     private String sha256Hex(String data) throws NoSuchAlgorithmException {
//         MessageDigest digest = MessageDigest.getInstance("SHA-256");
//         byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
//         StringBuilder sb = new StringBuilder(hash.length * 2);
//         for (byte b : hash) {
//             String hex = Integer.toHexString(0xff & b);
//             if (hex.length() == 1) sb.append('0');
//             sb.append(hex);
//         }
//         return sb.toString();
//     }
// }
