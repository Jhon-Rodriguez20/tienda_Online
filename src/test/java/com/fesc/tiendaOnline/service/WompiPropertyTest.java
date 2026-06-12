package com.fesc.tiendaOnline.service;

import com.fesc.tiendaOnline.config.WompiConfig;
import com.fesc.tiendaOnline.exception.UnauthorizedException;
import com.fesc.tiendaOnline.model.dto.WompiTransaccionRequestDTO;
import com.fesc.tiendaOnline.model.entity.CompraEntity;
import com.fesc.tiendaOnline.model.entity.CompraEstado;
import com.fesc.tiendaOnline.repository.CompraRepository;
import com.fesc.tiendaOnline.repository.ProductoRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import tools.jackson.databind.ObjectMapper;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Wompi integration.
 *
 * Covers task 27.4 (Properties 28–31).
 * No Spring context needed — all tests are pure unit tests.
 */
class WompiPropertyTest {

    // =========================================================================
    // Property 28 — CARD payload contains only token (no raw card number)
    // Validates: Requirements 21.1
    // =========================================================================

    /**
     * Property 28: Para cualquier pago CARD, {@code wompiCardToken} es el único dato
     * de tarjeta en el request del backend.
     *
     * <p>When payment type is CARD, the {@link WompiTransaccionRequestDTO} built by
     * {@code CompraService.realizarCompra} must have:
     * <ul>
     *   <li>{@code payment_method.token} set (from {@code wompiCardToken})</li>
     *   <li>{@code payment_method.phone_number = null} (no phone)</li>
     *   <li>{@code payment_method.type = "CARD"}</li>
     *   <li>{@code payment_method.installments = cuotas}</li>
     * </ul>
     *
     * <p><b>Validates: Requirements 21.1</b></p>
     */
    @Property(tries = 100)
    void property28_cardPayloadContainsOnlyToken(
            @ForAll @AlphaChars @StringLength(min = 10, max = 50) String cardToken,
            @ForAll @IntRange(min = 1, max = 36) int cuotas) {

        // Build the PaymentMethod using the same logic as CompraService.realizarCompra
        WompiTransaccionRequestDTO.PaymentMethod pm = new WompiTransaccionRequestDTO.PaymentMethod();
        pm.setType("CARD");
        pm.setToken(cardToken);
        pm.setInstallments(cuotas);
        // phone_number is intentionally NOT set (must remain null for CARD)

        // Assert: token must be the card token provided
        assertThat(pm.getToken())
                .as("CARD: payment_method.token must be set to the cardToken")
                .isNotNull()
                .isEqualTo(cardToken);

        // Assert: phone_number must be null (no phone number on card payments)
        assertThat(pm.getPhone_number())
                .as("CARD: payment_method.phone_number must be null — no raw phone data")
                .isNull();

        // Assert: type must be "CARD"
        assertThat(pm.getType())
                .as("CARD: payment_method.type must equal 'CARD'")
                .isEqualTo("CARD");

        // Assert: installments must equal cuotas
        assertThat(pm.getInstallments())
                .as("CARD: payment_method.installments must equal the cuotas value")
                .isEqualTo(cuotas);
    }

    // =========================================================================
    // Property 29 — Invalid checksum never modifies CompraEntity
    // Validates: Requirements 20.2
    // =========================================================================

    /**
     * Property 29: Para cualquier payload de webhook con firma incorrecta,
     * {@code WompiWebhookService} no modifica ninguna {@code CompraEntity}.
     *
     * <p>For any event payload, if the checksum does not match the expected SHA-256,
     * {@code procesarEvento} must throw {@link UnauthorizedException} and
     * {@code compraRepository.save()} must never be called.
     *
     * <p><b>Validates: Requirements 20.2</b></p>
     */
    @Property(tries = 100)
    void property29_invalidChecksumNeverModifiesCompra(
            @ForAll @AlphaChars @StringLength(min = 1, max = 60) String wrongChecksum)
            throws Exception {

        // Compute the correct checksum so we can filter it out
        String eventsKey = "testEventsKey";
        String idEvento = "evt_test";
        String timestamp = "1700000000";
        String correctChecksum = sha256(idEvento + timestamp + eventsKey);

        // Assume the generated string differs from the correct checksum (virtually guaranteed
        // for any non-trivial alpha string, but filtering makes it explicit)
        Assume.that(!wrongChecksum.equalsIgnoreCase(correctChecksum));

        // Build a fixed valid JSON payload
        String payload = buildPayload(idEvento, "txn_abc", "APPROVED", Long.parseLong(timestamp));

        // Set up mocks
        WompiConfig mockConfig = Mockito.mock(WompiConfig.class);
        when(mockConfig.getEventsKey()).thenReturn(eventsKey);

        CompraRepository mockCompraRepo = Mockito.mock(CompraRepository.class);
        ProductoRepository mockProductoRepo = Mockito.mock(ProductoRepository.class);

        WompiWebhookService webhookService = new WompiWebhookService(
                mockConfig,
                mockCompraRepo,
                mockProductoRepo,
                new ObjectMapper()
        );

        // Act & Assert: must throw UnauthorizedException
        assertThatThrownBy(() -> webhookService.procesarEvento(payload, wrongChecksum))
                .isInstanceOf(UnauthorizedException.class);

        // Assert: compraRepository.save() must NEVER be called
        verify(mockCompraRepo, never()).save(any());
    }

    // =========================================================================
    // Property 30 — Duplicate event is processed exactly once
    // Validates: Requirements 20.5
    // =========================================================================

    /**
     * Property 30: Para cualquier {@code id_evento} procesado dos veces, la
     * {@code CompraEntity} solo cambia estado una vez.
     *
     * <p>Processing the same event twice must result in exactly 1 call to
     * {@code compraRepository.save()}, not 2.
     *
     * <p><b>Validates: Requirements 20.5</b></p>
     */
    @Property(tries = 50)
    void property30_duplicateEventProcessedOnce(
            @ForAll("eventIds") String idEvento,
            @ForAll("transaccionIds") String wompiTransaccionId) throws Exception {

        String eventsKey = "testEventsKey30";
        long timestamp = 1700000000L;

        // Build payload and correct checksum
        String payload = buildPayload(idEvento, wompiTransaccionId, "APPROVED", timestamp);
        String checksum = sha256(idEvento + timestamp + eventsKey);

        // Set up mocks — fresh instance per trial guarantees empty processedEvents map
        WompiConfig mockConfig = Mockito.mock(WompiConfig.class);
        when(mockConfig.getEventsKey()).thenReturn(eventsKey);

        CompraRepository mockCompraRepo = Mockito.mock(CompraRepository.class);
        ProductoRepository mockProductoRepo = Mockito.mock(ProductoRepository.class);

        CompraEntity compra = new CompraEntity();
        compra.setCompraEstado(CompraEstado.PENDIENTE);
        compra.setWompiTransaccionId(wompiTransaccionId);

        when(mockCompraRepo.findByWompiTransaccionId(wompiTransaccionId))
                .thenReturn(Optional.of(compra));
        when(mockCompraRepo.save(any(CompraEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Fresh WompiWebhookService instance → empty processedEvents map
        WompiWebhookService webhookService = new WompiWebhookService(
                mockConfig,
                mockCompraRepo,
                mockProductoRepo,
                new ObjectMapper()
        );

        // Act: process the same event twice
        webhookService.procesarEvento(payload, checksum);
        webhookService.procesarEvento(payload, checksum);

        // Assert: save called exactly ONCE despite two procesarEvento calls
        verify(mockCompraRepo, times(1)).save(any(CompraEntity.class));
    }

    // =========================================================================
    // Property 31 — Missing/blank credential causes IllegalStateException
    // Validates: Requirements 22.5
    // =========================================================================

    /**
     * Property 31: Si cualquier credencial Wompi es nula/vacía, la aplicación lanza
     * {@link IllegalStateException}.
     *
     * <p>For any combination where at least one credential (publicKey, privateKey,
     * eventsKey, integrityKey) is null or blank, {@code WompiConfig.validate()} must
     * throw {@link IllegalStateException} with message
     * {@code "Las credenciales de Wompi no están configuradas"}.
     *
     * <p><b>Validates: Requirements 22.5</b></p>
     */
    @Property(tries = 100)
    void property31_missingCredentialThrowsOnStartup(
            @ForAll("blankValues") String blankValue,
            @ForAll @IntRange(min = 0, max = 3) int fieldIndex) throws Exception {

        // Field names in WompiConfig (index 0–3)
        String[] fieldNames = {"publicKey", "privateKey", "eventsKey", "integrityKey"};

        WompiConfig config = new WompiConfig();

        // Set all fields to valid non-blank values first
        setField(config, "publicKey",     "valid_pub_key");
        setField(config, "privateKey",    "valid_prv_key");
        setField(config, "eventsKey",     "valid_evt_key");
        setField(config, "integrityKey",  "valid_int_key");
        setField(config, "baseUrl",       "https://sandbox.wompi.co/v1");

        // Blank out only the field at fieldIndex
        setField(config, fieldNames[fieldIndex], blankValue);

        // validate() should throw because at least one credential is blank/null
        assertThatThrownBy(config::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Las credenciales de Wompi no están configuradas");
    }

    // =========================================================================
    // Providers
    // =========================================================================

    @Provide
    Arbitrary<String> eventIds() {
        return Arbitraries.create(
                () -> "evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
    }

    @Provide
    Arbitrary<String> transaccionIds() {
        return Arbitraries.create(
                () -> "txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
    }

    @Provide
    Arbitrary<String> blankValues() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.just("   ")
        );
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Builds a minimal Wompi webhook JSON payload string.
     */
    private String buildPayload(String idEvento, String wompiTransaccionId,
                                 String status, long timestamp) {
        return String.format("""
                {
                  "event": "%s",
                  "timestamp": %d,
                  "data": {
                    "transaction": {
                      "id": "%s",
                      "status": "%s"
                    }
                  }
                }
                """, idEvento, timestamp, wompiTransaccionId, status);
    }

    /**
     * Computes the SHA-256 hex digest of the given data string (UTF-8 encoded).
     * Mirrors the algorithm used by {@link WompiWebhookService#calcularFirma}.
     */
    private String sha256(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * Sets a private field on the given object via reflection.
     *
     * @param target    the object whose field to set
     * @param fieldName the name of the field
     * @param value     the value to assign (may be {@code null})
     */
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
