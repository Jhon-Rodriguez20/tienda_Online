package com.fesc.tiendaOnline.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReviewService}.
 * Requirements: 3.2, 3.6, 4.2, 4.3, 5.6, 6.2, 7.3
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private CompraRepository compraRepository;

    @Mock
    private CompraDetalleRepository compraDetalleRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private ReviewService reviewService;

    private UUID idUsuario;
    private UUID idProducto;
    private UUID idReview;
    private ProductoEntity producto;
    private UsuarioEntity usuario;

    @BeforeEach
    void setUp() {
        idUsuario = UUID.randomUUID();
        idProducto = UUID.randomUUID();
        idReview = UUID.randomUUID();

        producto = new ProductoEntity();
        producto.setIdProducto(idProducto);
        producto.setNombreProducto("Producto Test");

        usuario = new UsuarioEntity();
        usuario.setIdUsuario(idUsuario);
        usuario.setNombre("Juan");
    }

    // -----------------------------------------------------------------------
    // crearOActualizar - Producto no encontrado → NotFoundException
    // Validates: Requirement 3.6
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("crearOActualizar: producto no existente lanza NotFoundException")
    void crearOActualizar_productoNoExiste_lanzaNotFoundException() {
        ReviewCreateDTO dto = new ReviewCreateDTO();
        dto.setIdProducto(idProducto);
        dto.setEstrellas(4);
        dto.setComentario("Buen producto");

        when(productoRepository.findById(idProducto)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.crearOActualizar(idUsuario, dto))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Producto no encontrado");
    }

    // -----------------------------------------------------------------------
    // crearOActualizar - Sin compra calificada → ForbiddenException
    // Validates: Requirement 3.2
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("crearOActualizar: sin compra calificada lanza ForbiddenException")
    void crearOActualizar_sinCompraCalificada_lanzaForbiddenException() {
        ReviewCreateDTO dto = new ReviewCreateDTO();
        dto.setIdProducto(idProducto);
        dto.setEstrellas(5);
        dto.setComentario("Excelente");

        when(productoRepository.findById(idProducto)).thenReturn(Optional.of(producto));

        List<CompraEstado> estadosValidos = Arrays.asList(CompraEstado.ACEPTADO, CompraEstado.ENTREGADO);
        when(compraRepository.existsByUsuarioAndEstadoAndProducto(eq(idUsuario), eq(estadosValidos), eq(idProducto)))
                .thenReturn(false);

        assertThatThrownBy(() -> reviewService.crearOActualizar(idUsuario, dto))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Solo puedes reseñar productos que hayas comprado");
    }

    // -----------------------------------------------------------------------
    // eliminar - Reseña no encontrada → NotFoundException
    // Validates: Requirement 4.3
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("eliminar: reseña no existente lanza NotFoundException")
    void eliminar_resenaNoExiste_lanzaNotFoundException() {
        when(reviewRepository.findById(idReview)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.eliminar(idReview, idUsuario))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Reseña no encontrada");
    }

    // -----------------------------------------------------------------------
    // eliminar - No es propietario → ForbiddenException
    // Validates: Requirement 4.2
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("eliminar: usuario no propietario lanza ForbiddenException")
    void eliminar_noPropietario_lanzaForbiddenException() {
        UUID otroUsuarioId = UUID.randomUUID();
        UsuarioEntity otroUsuario = new UsuarioEntity();
        otroUsuario.setIdUsuario(otroUsuarioId);

        ReviewEntity review = new ReviewEntity();
        review.setIdReview(idReview);
        review.setUsuario(otroUsuario);

        when(reviewRepository.findById(idReview)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.eliminar(idReview, idUsuario))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("No tienes permiso para eliminar esta reseña");
    }

    // -----------------------------------------------------------------------
    // eliminar - Éxito → verifyDelete called
    // Validates: Requirement 4.3
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("eliminar: éxito invoca delete en el repositorio")
    void eliminar_exito_invocaDelete() {
        ReviewEntity review = new ReviewEntity();
        review.setIdReview(idReview);
        review.setUsuario(usuario);

        when(reviewRepository.findById(idReview)).thenReturn(Optional.of(review));

        reviewService.eliminar(idReview, idUsuario);

        verify(reviewRepository).delete(review);
    }

    // -----------------------------------------------------------------------
    // obtenerEstadisticas - Sin reseñas → 0.0 / 0
    // Validates: Requirement 7.3
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("obtenerEstadisticas: sin reseñas retorna promedio 0.0 y total 0")
    void obtenerEstadisticas_sinResenas_retornaCeros() {
        when(productoRepository.findById(idProducto)).thenReturn(Optional.of(producto));
        when(reviewRepository.promedioEstrellasPorProducto(idProducto)).thenReturn(Optional.empty());
        when(reviewRepository.contarPorProducto(idProducto)).thenReturn(0L);

        ReviewEstadisticasDTO result = reviewService.obtenerEstadisticas(idProducto);

        assertThat(result.getPromedioEstrellas()).isEqualTo(0.0);
        assertThat(result.getTotalResenas()).isEqualTo(0L);
    }

    // -----------------------------------------------------------------------
    // obtenerReviewUsuario - Sin reseña → NotFoundException
    // Validates: Requirement 6.2
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("obtenerReviewUsuario: sin reseña lanza NotFoundException")
    void obtenerReviewUsuario_sinResena_lanzaNotFoundException() {
        when(productoRepository.findById(idProducto)).thenReturn(Optional.of(producto));
        when(reviewRepository.findByUsuarioIdUsuarioAndProductoIdProducto(idUsuario, idProducto))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.obtenerReviewUsuario(idUsuario, idProducto))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No has dejado una reseña para este producto");
    }

    // -----------------------------------------------------------------------
    // obtenerPorProducto - Página fuera de rango → contenido vacío
    // Validates: Requirement 5.6
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("obtenerPorProducto: página fuera de rango retorna contenido vacío")
    void obtenerPorProducto_paginaFueraDeRango_retornaContenidoVacio() {
        when(productoRepository.findById(idProducto)).thenReturn(Optional.of(producto));

        Page<ReviewEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(reviewRepository.findByProductoIdProducto(eq(idProducto), any(Pageable.class)))
                .thenReturn(emptyPage);

        PaginacionResponseDTO<ReviewResponseDTO> result =
                reviewService.obtenerPorProducto(idProducto, 999, 10);

        assertThat(result.getContenido()).isEmpty();
        assertThat(result.getTotalElementos()).isZero();
    }
}
