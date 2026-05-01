package com.fesc.tiendaOnline.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fesc.tiendaOnline.model.dto.PaginacionResponseDTO;
import com.fesc.tiendaOnline.model.dto.ProductoBusquedaDTO;
import com.fesc.tiendaOnline.model.dto.ProductoCreateDTO;
import com.fesc.tiendaOnline.model.dto.ProductoResponseDTO;
import com.fesc.tiendaOnline.model.dto.ProductoUpdateDTO;
import com.fesc.tiendaOnline.security.UserDetailsImpl;
import com.fesc.tiendaOnline.service.ProductoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    public ResponseEntity<PaginacionResponseDTO<ProductoResponseDTO>> listProductos(@RequestParam(defaultValue = "0") int pagina,
                                                                    @RequestParam(defaultValue = "10") int tamanio) {
        if (tamanio != 10 && tamanio != 25 && tamanio != 50) {
            tamanio = 10;
        }

        PaginacionResponseDTO<ProductoResponseDTO> productos = productoService.getProductos(pagina, tamanio);
        return ResponseEntity.ok(productos);
    }

    @GetMapping("/buscar")
    public ResponseEntity<PaginacionResponseDTO<ProductoResponseDTO>> buscarPorTermino(@RequestParam String termino,
                                                                    @RequestParam(defaultValue = "0") int pagina,
                                                                    @RequestParam(defaultValue = "10") int tamanio) {
        if (tamanio != 10 && tamanio != 25 && tamanio != 50) {
            tamanio = 10;
        }

        PaginacionResponseDTO<ProductoResponseDTO> productos = productoService.buscarProductosPorTermino(termino, pagina, tamanio);
        return ResponseEntity.ok(productos);
    }

    @GetMapping("/buscar/nombre")
    public ResponseEntity<PaginacionResponseDTO<ProductoResponseDTO>> buscarPorNombre(@RequestParam String nombre,
                                                                    @RequestParam(defaultValue = "0") int pagina,
                                                                    @RequestParam(defaultValue = "10") int tamanio) {
        if (tamanio != 10 && tamanio != 25 && tamanio != 50) {
            tamanio = 10;
        }

        PaginacionResponseDTO<ProductoResponseDTO> productos = productoService.buscarProductosPorNombre(nombre, pagina, tamanio);
        return ResponseEntity.ok(productos);
    }

    @PostMapping("/buscar/avanzado")
    public ResponseEntity<PaginacionResponseDTO<ProductoResponseDTO>> buscarAvanzado(@RequestBody ProductoBusquedaDTO productoBusquedaDTO) {
        PaginacionResponseDTO<ProductoResponseDTO> productos = productoService.buscarProductosAvanzado(productoBusquedaDTO);
        return ResponseEntity.ok(productos);
    }

    @GetMapping("/{idProducto}")
    public ResponseEntity<ProductoResponseDTO> findProductoById(@PathVariable UUID idProducto) {
        return ResponseEntity.ok(productoService.getProductoById(idProducto));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductoResponseDTO> crearProducto(@Valid @ModelAttribute ProductoCreateDTO productoCreateDTO) {
        UUID idAdmin = obtenerIdUsuarioAutenticado();
        ProductoResponseDTO productoCreado = productoService.crearProducto(productoCreateDTO, idAdmin);
        return new ResponseEntity<>(productoCreado, HttpStatus.CREATED);
    }

    @PutMapping(value = "/{idProducto}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductoResponseDTO> actualizarProducto(@PathVariable UUID idProducto,
                                                            @Valid @ModelAttribute ProductoUpdateDTO productoUpdateDTO) {
        UUID idAdmin = obtenerIdUsuarioAutenticado();
        ProductoResponseDTO productoActualizado = productoService.actualizarProducto(idProducto, productoUpdateDTO, idAdmin);
        return ResponseEntity.ok(productoActualizado);
    }

    @DeleteMapping(value = "/{idProducto}")
    public ResponseEntity<Map<String, String>> eliminarProducto(@PathVariable UUID idProducto) {
        UUID idAdmin = obtenerIdUsuarioAutenticado();
        productoService.eliminarProducto(idProducto, idAdmin);

        Map<String, String> response = new HashMap<>();
        response.put("mensaje", "Producto eliminado exitosamente");
        response.put("status", "success");

        return ResponseEntity.ok(response);
    }

    private UUID obtenerIdUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetailsImpl = (UserDetailsImpl) authentication.getPrincipal();

        return userDetailsImpl.getUsuario().getIdUsuario();
    }
}
