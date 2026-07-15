package com.fesc.tiendaOnline.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fesc.tiendaOnline.exception.ForbiddenException;
import com.fesc.tiendaOnline.exception.NotFoundException;
import com.fesc.tiendaOnline.model.dto.PaginacionResponseDTO;
import com.fesc.tiendaOnline.model.dto.ReviewCreateDTO;
import com.fesc.tiendaOnline.model.dto.ReviewEstadisticasDTO;
import com.fesc.tiendaOnline.model.dto.ReviewResponseDTO;
import com.fesc.tiendaOnline.model.entity.CompraEstado;
import com.fesc.tiendaOnline.model.entity.ProductoEntity;
import com.fesc.tiendaOnline.model.entity.ReviewEntity;
import com.fesc.tiendaOnline.model.entity.UsuarioEntity;
import com.fesc.tiendaOnline.repository.CompraDetalleRepository;
import com.fesc.tiendaOnline.repository.CompraRepository;
import com.fesc.tiendaOnline.repository.ProductoRepository;
import com.fesc.tiendaOnline.repository.ReviewRepository;
import com.fesc.tiendaOnline.repository.UsuarioRepository;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductoRepository productoRepository;
    private final CompraRepository compraRepository;
    private final CompraDetalleRepository compraDetalleRepository;
    private final UsuarioRepository usuarioRepository;

    private static final List<Integer> TAMANIOS_PERMITIDOS = Arrays.asList(10, 25, 50);

    public ReviewService(ReviewRepository reviewRepository,
                         ProductoRepository productoRepository,
                         CompraRepository compraRepository,
                         CompraDetalleRepository compraDetalleRepository,
                         UsuarioRepository usuarioRepository) {
        this.reviewRepository = reviewRepository;
        this.productoRepository = productoRepository;
        this.compraRepository = compraRepository;
        this.compraDetalleRepository = compraDetalleRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public ReviewResponseDTO crearOActualizar(UUID idUsuario, ReviewCreateDTO dto) {
        // Validate product exists
        ProductoEntity producto = productoRepository.findById(dto.getIdProducto())
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));

        // Verify qualifying purchase (ACEPTADO or ENTREGADO with product in details)
        List<CompraEstado> estadosValidos = Arrays.asList(CompraEstado.ACEPTADO, CompraEstado.ENTREGADO);
        boolean tieneCompraValida = compraRepository.existsByUsuarioAndEstadoAndProducto(
                idUsuario, estadosValidos, dto.getIdProducto());

        if (!tieneCompraValida) {
            throw new ForbiddenException("Solo puedes reseñar productos que hayas comprado");
        }

        // Upsert logic
        ReviewEntity review = reviewRepository
                .findByUsuarioIdUsuarioAndProductoIdProducto(idUsuario, dto.getIdProducto())
                .orElse(null);

        LocalDateTime now = LocalDateTime.now();
        String comentarioTrimmed = dto.getComentario().trim();

        if (review != null) {
            // Update existing review - preserve createdAt
            review.setEstrellas(dto.getEstrellas());
            review.setComentario(comentarioTrimmed);
            review.setUpdatedAt(now);
        } else {
            // Create new review
            UsuarioEntity usuario = usuarioRepository.findById(idUsuario)
                    .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

            review = new ReviewEntity();
            review.setProducto(producto);
            review.setUsuario(usuario);
            review.setEstrellas(dto.getEstrellas());
            review.setComentario(comentarioTrimmed);
            review.setCreatedAt(now);
            review.setUpdatedAt(now);
        }

        ReviewEntity savedReview = reviewRepository.save(review);
        return mapToResponseDTO(savedReview);
    }

    @Transactional
    public void eliminar(UUID idReview, UUID idUsuario) {
        // Find review
        ReviewEntity review = reviewRepository.findById(idReview)
                .orElseThrow(() -> new NotFoundException("Reseña no encontrada"));

        // Verify ownership
        if (!review.getUsuario().getIdUsuario().equals(idUsuario)) {
            throw new ForbiddenException("No tienes permiso para eliminar esta reseña");
        }

        reviewRepository.delete(review);
    }

    @Transactional(readOnly = true)
    public PaginacionResponseDTO<ReviewResponseDTO> obtenerPorProducto(UUID idProducto, int pagina, int tamanio) {
        // Validate product exists
        productoRepository.findById(idProducto)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));

        // Normalize tamanio
        int tamanioNormalizado = TAMANIOS_PERMITIDOS.contains(tamanio) ? tamanio : 10;

        // Create pageable sorted by createdAt DESC
        Pageable pageable = PageRequest.of(pagina, tamanioNormalizado, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Query
        Page<ReviewEntity> page = reviewRepository.findByProductoIdProducto(idProducto, pageable);

        // Map to response DTO
        List<ReviewResponseDTO> contenido = page.getContent().stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());

        return new PaginacionResponseDTO<>(
                contenido,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast(),
                page.isFirst()
        );
    }

    @Transactional(readOnly = true)
    public ReviewEstadisticasDTO obtenerEstadisticas(UUID idProducto) {
        // Validate product exists
        productoRepository.findById(idProducto)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));

        // Get average and count
        Double promedio = reviewRepository.promedioEstrellasPorProducto(idProducto).orElse(null);
        Long total = reviewRepository.contarPorProducto(idProducto);

        ReviewEstadisticasDTO estadisticas = new ReviewEstadisticasDTO();

        if (promedio == null || total == 0) {
            estadisticas.setPromedioEstrellas(0.0);
            estadisticas.setTotalResenas(0L);
        } else {
            // Round to 1 decimal using HALF_UP
            BigDecimal promedioRedondeado = BigDecimal.valueOf(promedio)
                    .setScale(1, RoundingMode.HALF_UP);
            estadisticas.setPromedioEstrellas(promedioRedondeado.doubleValue());
            estadisticas.setTotalResenas(total);
        }

        return estadisticas;
    }

    @Transactional(readOnly = true)
    public ReviewResponseDTO obtenerReviewUsuario(UUID idUsuario, UUID idProducto) {
        // Validate product exists
        productoRepository.findById(idProducto)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));

        // Find review by user and product
        ReviewEntity review = reviewRepository
                .findByUsuarioIdUsuarioAndProductoIdProducto(idUsuario, idProducto)
                .orElseThrow(() -> new NotFoundException("No has dejado una reseña para este producto"));

        return mapToResponseDTO(review);
    }

    private ReviewResponseDTO mapToResponseDTO(ReviewEntity review) {
        ReviewResponseDTO dto = new ReviewResponseDTO();
        dto.setIdReview(review.getIdReview());
        dto.setIdProducto(review.getProducto().getIdProducto());
        dto.setIdUsuario(review.getUsuario().getIdUsuario());
        dto.setNombreUsuario(review.getUsuario().getNombre());
        dto.setEstrellas(review.getEstrellas());
        dto.setComentario(review.getComentario());
        dto.setCreatedAt(review.getCreatedAt());
        dto.setUpdatedAt(review.getUpdatedAt());
        return dto;
    }
}
