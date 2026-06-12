package com.fesc.tiendaOnline.service;

import com.fesc.tiendaOnline.config.WompiConfig;
import com.fesc.tiendaOnline.exception.UnauthorizedException;
import com.fesc.tiendaOnline.model.entity.CompraDetalleEntity;
import com.fesc.tiendaOnline.model.entity.CompraEntity;
import com.fesc.tiendaOnline.model.entity.CompraEstado;
import com.fesc.tiendaOnline.model.entity.ProductoEntity;
import com.fesc.tiendaOnline.repository.CompraRepository;
import com.fesc.tiendaOnline.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WompiWebhookService}.
 *
 * Tests cover:
 *   1. Valid signature + APPROVED event → compra updated to ACEPTADO
 *   2. Invalid signature → UnauthorizedException, no save
 *   3. Duplicate event (same id_evento) → save called exactly once
 *   4. DECLINED event → stock restored and compra updated to CANCELADO
 */
class WompiWebhookServiceTest {

    private static final String EVENTS_KEY = "testEventsKey";

    private WompiConfig wompiConfig;
    private CompraRepository compraRepository;
    private ProductoRepository productoRepository;
    private WompiWebhookService service;

    @BeforeEach
    void setUp() {
        wompiConfig = mock(WompiConfig.class);
        compraRepository = mock(CompraRepository.class);
        productoRepository = mock(ProductoRepository.class);

        when(wompiConfig.getEventsKey()).thenReturn(EVENTS_KEY);

        service = new WompiWebhookService(
                wompiConfig,
                compraRepository,
                productoRepository,
                new ObjectMapper()
        );
    }

    // ─── Test 1: Valid signature + APPROVED → compra updated to ACEPTADO ────────

    @Test
    void givenValidSignatureAndApprovedEvent_whenProcesarEvento_thenCompraSavedAsAceptado()
            throws Exception {

        // Arrange
        String payload = """
                {
                  "event": "evt_001",
                  "timestamp": 1700000000,
                  "data": {
                    "transaction": {
                      "id": "txn_abc",
                      "status": "APPROVED"
                    }
                  }
                }
                """;

        String checksum = sha256("evt_001" + "1700000000" + EVENTS_KEY);

        CompraEntity compra = new CompraEntity();
        compra.setCompraEstado(CompraEstado.PENDIENTE);
        compra.setWompiTransaccionId("txn_abc");

        when(compraRepository.findByWompiTransaccionId("txn_abc"))
                .thenReturn(Optional.of(compra));
        when(compraRepository.save(any(CompraEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.procesarEvento(payload, checksum);

        // Assert
        ArgumentCaptor<CompraEntity> captor = ArgumentCaptor.forClass(CompraEntity.class);
        verify(compraRepository).save(captor.capture());

        assertThat(captor.getValue().getCompraEstado())
                .as("Compra estado debe ser ACEPTADO tras evento APPROVED")
                .isEqualTo(CompraEstado.ACEPTADO);
    }

    // ─── Test 2: Invalid signature → UnauthorizedException, save never called ───

    @Test
    void givenInvalidSignature_whenProcesarEvento_thenUnauthorizedExceptionAndNoSave() {

        String payload = """
                {
                  "event": "evt_001",
                  "timestamp": 1700000000,
                  "data": {
                    "transaction": {
                      "id": "txn_abc",
                      "status": "APPROVED"
                    }
                  }
                }
                """;

        // Act & Assert
        assertThatThrownBy(() -> service.procesarEvento(payload, "wrong_checksum"))
                .isInstanceOf(UnauthorizedException.class);

        verify(compraRepository, never()).save(any());
    }

    // ─── Test 3: Duplicate event → save called exactly once ─────────────────────

    @Test
    void givenDuplicateEvent_whenProcesarEventoTwice_thenSaveCalledOnlyOnce()
            throws Exception {

        // Arrange
        String payload = """
                {
                  "event": "evt_dup_001",
                  "timestamp": 1700001000,
                  "data": {
                    "transaction": {
                      "id": "txn_dup",
                      "status": "APPROVED"
                    }
                  }
                }
                """;

        String checksum = sha256("evt_dup_001" + "1700001000" + EVENTS_KEY);

        CompraEntity compra = new CompraEntity();
        compra.setCompraEstado(CompraEstado.PENDIENTE);
        compra.setWompiTransaccionId("txn_dup");

        when(compraRepository.findByWompiTransaccionId("txn_dup"))
                .thenReturn(Optional.of(compra));
        when(compraRepository.save(any(CompraEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act: process the same event twice
        service.procesarEvento(payload, checksum);
        service.procesarEvento(payload, checksum);

        // Assert: save must have been called exactly once
        verify(compraRepository, times(1)).save(any());
    }

    // ─── Test 4: DECLINED → stock restored + compra CANCELADO ───────────────────

    @Test
    void givenDeclinedEvent_whenProcesarEvento_thenStockRestoredAndCompraCancelado()
            throws Exception {

        // Arrange
        UUID productId = UUID.randomUUID();

        ProductoEntity producto = new ProductoEntity();
        producto.setIdProducto(productId);
        producto.setStockProducto(10);

        CompraDetalleEntity detalle = new CompraDetalleEntity();
        detalle.setProducto(producto);
        detalle.setCantidad(3);

        CompraEntity compra = new CompraEntity();
        compra.setCompraEstado(CompraEstado.PENDIENTE);
        compra.setWompiTransaccionId("txn_declined");
        compra.addDetalle(detalle);

        String payload = """
                {
                  "event": "evt_dec_001",
                  "timestamp": 1700002000,
                  "data": {
                    "transaction": {
                      "id": "txn_declined",
                      "status": "DECLINED"
                    }
                  }
                }
                """;

        String checksum = sha256("evt_dec_001" + "1700002000" + EVENTS_KEY);

        when(compraRepository.findByWompiTransaccionId("txn_declined"))
                .thenReturn(Optional.of(compra));
        when(compraRepository.save(any(CompraEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(productoRepository.findByIdWithLock(productId))
                .thenReturn(Optional.of(producto));
        when(productoRepository.save(any(ProductoEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.procesarEvento(payload, checksum);

        // Assert: stock restored (10 + 3 = 13)
        assertThat(producto.getStockProducto())
                .as("Stock debe ser restaurado a 13 (10 + 3)")
                .isEqualTo(13);

        // Assert: compra marked as CANCELADO
        ArgumentCaptor<CompraEntity> compraCaptor = ArgumentCaptor.forClass(CompraEntity.class);
        verify(compraRepository).save(compraCaptor.capture());
        assertThat(compraCaptor.getValue().getCompraEstado())
                .as("Compra estado debe ser CANCELADO tras evento DECLINED")
                .isEqualTo(CompraEstado.CANCELADO);

        // Assert: both repositories had save called
        verify(productoRepository).save(any(ProductoEntity.class));
        verify(compraRepository).save(any(CompraEntity.class));
    }

    // ─── SHA-256 helper ─────────────────────────────────────────────────────────

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
}
