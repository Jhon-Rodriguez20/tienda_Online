package com.fesc.tiendaOnline.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fesc.tiendaOnline.model.dto.ActualizarEstadoCompraDTO;
import com.fesc.tiendaOnline.model.dto.CompraBusquedaDTO;
import com.fesc.tiendaOnline.model.dto.CompraMetodoPagoResponseDTO;
import com.fesc.tiendaOnline.model.dto.CompraRequestDTO;
import com.fesc.tiendaOnline.model.dto.CompraResponseDTO;
import com.fesc.tiendaOnline.model.dto.IdempotencyResult;
import com.fesc.tiendaOnline.model.dto.PaginacionResponseDTO;
import com.fesc.tiendaOnline.security.UserDetailsImpl;
import com.fesc.tiendaOnline.service.CompraService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/compras")
public class CompraController {

    /** Patrón UUID v4: 8-4-4-4-12 hex, variante RFC 4122, versión 4. */
    private static final Pattern UUID_V4_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    private final CompraService compraService;

    public CompraController(CompraService compraService) {
        this.compraService = compraService;
    }

    // ======= CLIENTE =================

    @PostMapping("/realizar")
    public ResponseEntity<?> realizarCompra(
            @Valid @RequestBody CompraRequestDTO requestDTO,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        // 1. Validar presencia del header
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El header Idempotency-Key es obligatorio"));
        }

        // 2. Validar formato UUID v4
        if (!UUID_V4_PATTERN.matcher(idempotencyKey).matches()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El header Idempotency-Key debe ser un UUID v4 válido"));
        }

        UUID usuarioId = obtenerIdUsuarioAutenticado();

        // 3. Llamar al servicio
        IdempotencyResult<CompraResponseDTO> result = compraService.realizarCompra(requestDTO, usuarioId, idempotencyKey);

        // 4. Añadir header de replay si aplica
        HttpHeaders headers = new HttpHeaders();
        if (result.replayed()) {
            headers.add("Idempotency-Replayed", "true");
        }

        return new ResponseEntity<>(result.data(), headers, HttpStatus.CREATED);
    }

    @PostMapping("/mis-compras")
    public ResponseEntity<PaginacionResponseDTO<CompraResponseDTO>> getMiscompras(@RequestBody CompraBusquedaDTO busquedaDTO) {
        UUID usuarioId = obtenerIdUsuarioAutenticado();
        PaginacionResponseDTO<CompraResponseDTO> compras = compraService.getMisCompras(usuarioId, busquedaDTO);
        return ResponseEntity.ok(compras);
    }
    
    @GetMapping("/metodo/pago")
    public ResponseEntity<List<CompraMetodoPagoResponseDTO>> listMetodosPagos() {
        return ResponseEntity.ok(compraService.getMetodoPago());
    }

    @GetMapping("/{compraId}")
    public ResponseEntity<CompraResponseDTO> getCompraById(@PathVariable UUID compraId) {
        UUID usuarioId = obtenerIdUsuarioAutenticado();
        CompraResponseDTO compraResponseDTO = compraService.getCompraById(compraId, usuarioId);
        return ResponseEntity.ok(compraResponseDTO);
    }

    @DeleteMapping("/{compraId}/cancelar")
    public ResponseEntity<Map<String, String>> cancelarCompra(@PathVariable UUID compraId) {
        UUID usuarioId = obtenerIdUsuarioAutenticado();
        compraService.cancelarCompra(compraId, usuarioId);

        Map<String, String> response = new HashMap<>();
        response.put("mensaje", "Compra cancelada exitosamente");
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    // ======= ADMIN =================
    @PostMapping("/admin/todas")
    public ResponseEntity<PaginacionResponseDTO<CompraResponseDTO>> getAllCompras(@RequestBody CompraBusquedaDTO compraBusquedaDTO) {
        UUID idAdmin = obtenerIdUsuarioAutenticado();
        PaginacionResponseDTO<CompraResponseDTO> compras = compraService.getAllCompras(compraBusquedaDTO, idAdmin);
        return ResponseEntity.ok(compras);
    }

    @PutMapping("/admin/{compraId}/estado")
    public ResponseEntity<?> actualizarEstadoCompra(
            @PathVariable UUID compraId,
            @Valid @RequestBody ActualizarEstadoCompraDTO request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        // 1. Validar presencia del header
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El header Idempotency-Key es obligatorio"));
        }

        // 2. Validar formato UUID v4
        if (!UUID_V4_PATTERN.matcher(idempotencyKey).matches()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El header Idempotency-Key debe ser un UUID v4 válido"));
        }

        UUID adminId = obtenerIdUsuarioAutenticado();

        // 3. Llamar al servicio — cuando task 5.3 actualice la firma, retornará
        IdempotencyResult<CompraResponseDTO> result = compraService.putEstadoCompra(compraId, request, adminId, idempotencyKey);

        // 4. Añadir header de replay si aplica
        HttpHeaders headers = new HttpHeaders();
        if (result.replayed()) {
            headers.add("Idempotency-Replayed", "true");
        }

        return new ResponseEntity<>(result.data(), headers, HttpStatus.OK);
    }

    private UUID obtenerIdUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetailsImpl = (UserDetailsImpl) authentication.getPrincipal();
        return userDetailsImpl.getUsuario().getIdUsuario();
    }
}
