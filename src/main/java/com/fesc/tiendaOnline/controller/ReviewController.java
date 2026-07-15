package com.fesc.tiendaOnline.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fesc.tiendaOnline.model.dto.PaginacionResponseDTO;
import com.fesc.tiendaOnline.model.dto.ReviewCreateDTO;
import com.fesc.tiendaOnline.model.dto.ReviewEstadisticasDTO;
import com.fesc.tiendaOnline.model.dto.ReviewResponseDTO;
import com.fesc.tiendaOnline.security.UserDetailsImpl;
import com.fesc.tiendaOnline.service.ReviewService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<ReviewResponseDTO> crearOActualizarReview(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody ReviewCreateDTO reviewCreateDTO) {

        UUID idUsuario = principal.getUsuario().getIdUsuario();
        ReviewResponseDTO result = reviewService.crearOActualizar(idUsuario, reviewCreateDTO);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{idReview}")
    public ResponseEntity<Void> eliminarReview(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable UUID idReview) {

        UUID idUsuario = principal.getUsuario().getIdUsuario();
        reviewService.eliminar(idReview, idUsuario);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/producto/{idProducto}")
    public ResponseEntity<PaginacionResponseDTO<ReviewResponseDTO>> obtenerReviewsProducto(
            @PathVariable UUID idProducto,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamanio) {

        PaginacionResponseDTO<ReviewResponseDTO> result = reviewService.obtenerPorProducto(idProducto, pagina, tamanio);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/producto/{idProducto}/estadisticas")
    public ResponseEntity<ReviewEstadisticasDTO> obtenerEstadisticas(@PathVariable UUID idProducto) {
        ReviewEstadisticasDTO result = reviewService.obtenerEstadisticas(idProducto);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/producto/{idProducto}/mi-review")
    public ResponseEntity<ReviewResponseDTO> obtenerMiReview(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable UUID idProducto) {
                
        UUID idUsuario = principal.getUsuario().getIdUsuario();
        ReviewResponseDTO result = reviewService.obtenerReviewUsuario(idUsuario, idProducto);
        return ResponseEntity.ok(result);
    }
}
