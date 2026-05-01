package com.fesc.tiendaOnline.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fesc.tiendaOnline.model.dto.ActualizarEstadoCompraDTO;
import com.fesc.tiendaOnline.model.dto.CompraBusquedaDTO;
import com.fesc.tiendaOnline.model.dto.CompraRequestDTO;
import com.fesc.tiendaOnline.model.dto.CompraResponseDTO;
import com.fesc.tiendaOnline.model.dto.PaginacionResponseDTO;
import com.fesc.tiendaOnline.security.UserDetailsImpl;
import com.fesc.tiendaOnline.service.CompraService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/compras")
public class CompraController {

    private final CompraService compraService;

    public CompraController(CompraService compraService) {
        this.compraService = compraService;
    }

    // ======= CLIENTE =================

    @PostMapping("/realizar")
    public ResponseEntity<CompraResponseDTO> realizarCompra(@Valid @RequestBody CompraRequestDTO requestDTO) {
        UUID usuarioId = obtenerIdUsuarioAutenticado();
        CompraResponseDTO compraResponseDTO = compraService.realizarCompra(requestDTO, usuarioId);
        return new ResponseEntity<>(compraResponseDTO, HttpStatus.CREATED);
    }

    @PostMapping("/mis-compras")
    public ResponseEntity<PaginacionResponseDTO<CompraResponseDTO>> getMiscompras(@RequestBody CompraBusquedaDTO busquedaDTO) {
        UUID usuarioId = obtenerIdUsuarioAutenticado();
        PaginacionResponseDTO<CompraResponseDTO> compras = compraService.getMisCompras(usuarioId, busquedaDTO);
        return ResponseEntity.ok(compras);
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
    public ResponseEntity<CompraResponseDTO> actualizarEstadoCompra(@PathVariable UUID compraId, @Valid
                                                                    @RequestBody ActualizarEstadoCompraDTO request) {
        UUID adminId = obtenerIdUsuarioAutenticado();
        CompraResponseDTO compraResponseDTO = compraService.putEstadoCompra(compraId, request, adminId);
        return ResponseEntity.ok(compraResponseDTO);
    }

    private UUID obtenerIdUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetailsImpl = (UserDetailsImpl) authentication.getPrincipal();
        return userDetailsImpl.getUsuario().getIdUsuario();
    }
}
