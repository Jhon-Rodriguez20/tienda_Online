package com.fesc.tiendaOnline.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.fesc.tiendaOnline.exception.BusinessRuleException;
import com.fesc.tiendaOnline.model.dto.PaginacionResponseDTO;
import com.fesc.tiendaOnline.model.dto.ProductoBusquedaDTO;
import com.fesc.tiendaOnline.model.dto.ProductoCategoriaResponseDTO;
import com.fesc.tiendaOnline.model.dto.ProductoCreateDTO;
import com.fesc.tiendaOnline.model.dto.ProductoResponseDTO;
import com.fesc.tiendaOnline.model.dto.ProductoUpdateDTO;
import com.fesc.tiendaOnline.model.entity.CategoriaEntity;
import com.fesc.tiendaOnline.model.entity.ProductoEntity;
import com.fesc.tiendaOnline.repository.CategoriaRepository;
import com.fesc.tiendaOnline.repository.ProductoRepository;
import com.fesc.tiendaOnline.mapper.ProductoMapper;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductoService {

    private final AdminValidationService adminValidationService;
    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final FileStorageService fileStorageService;
    private final ProductoMapper productoMapper;

    public ProductoService(ProductoRepository productoRepository,
                        CategoriaRepository categoriaRepository,
                        FileStorageService fileStorageService,
                        AdminValidationService adminValidationService,
                        ProductoMapper productoMapper) {
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.fileStorageService = fileStorageService;
        this.adminValidationService = adminValidationService;
        this.productoMapper = productoMapper;
    }

    // LISTAR PRODUCTOS CON PAGINACIÓN - DISPONIBLE PARA TODOS LOS USUARIOS
    @Transactional(readOnly = true)
    @Cacheable(value = "productos", key = "#pagina + '-' + #tamanio")
    public PaginacionResponseDTO<ProductoResponseDTO> getProductos(int pagina, int tamanio) {
        Pageable pageable = PageRequest.of(pagina, tamanio);
        Page<ProductoEntity> paginaProductos = productoRepository.findAllWithDetails(pageable);
        return convertirAPaginacionResponse(paginaProductos);
    }

    //BUSCAR PRODUCTOS POR TERMINO (NOMBRE O DESCRIPCION) - DISPONIBLE PARA TODOS LOS USUARIOS
    @Transactional(readOnly = true)
    @Cacheable(value = "busquedaProductos", key = "#termino + '-' + #pagina + '-' + #tamanio")
    public PaginacionResponseDTO<ProductoResponseDTO> buscarProductosPorTermino(String termino, int pagina, int tamanio) {
        Pageable pageable = PageRequest.of(pagina, tamanio);
        if (termino == null || termino.trim().isEmpty()) {
            return getProductos(pagina, tamanio);
        }
        Page<ProductoEntity> paginaProductos = productoRepository.buscarPorTermino(termino.trim(), pageable);
        return convertirAPaginacionResponse(paginaProductos);
    }

    // BUSCAR PRODUCTOS POR NOMBRE EXACTAMENTE - DISPONIBLE PARA TODOS LOS USUARIOS
    @Transactional(readOnly = true)
    @Cacheable(value = "busquedaProductos", key = "#nombre + '-' + #pagina + '-' + #tamanio")
    public PaginacionResponseDTO<ProductoResponseDTO> buscarProductosPorNombre(String nombre, int pagina, int tamanio) {
        Pageable pageable = PageRequest.of(pagina, tamanio);
        if (nombre == null || nombre.trim().isEmpty()) {
            return getProductos(pagina, tamanio);
        }
        Page<ProductoEntity> paginaProductos = productoRepository.buscarPorNombre(nombre.trim(), pageable);
        return convertirAPaginacionResponse(paginaProductos);
    }

    //BUSCAR CATEGORÍAS DE PRODUCTOS - ADMIN (con validación)
    @Transactional(readOnly = true)
    public List<ProductoCategoriaResponseDTO> getCategorias(UUID idAdmin) {
        adminValidationService.validarAdmin(idAdmin);
        return categoriaRepository.findAll().stream()
            .map(this::productoCategoriaConvertirAResponse)
            .collect(Collectors.toList());
    }

    //BUSCAR CATEGORÍAS DE PRODUCTOS - PÚBLICO (sin validación)
    @Transactional(readOnly = true)
    public List<ProductoCategoriaResponseDTO> getCategoriasPublic() {
        return categoriaRepository.findAll().stream()
            .map(this::productoCategoriaConvertirAResponse)
            .collect(Collectors.toList());
    }

    // BUSQUEDA AVANZADA CON MULTIPLES FILTROS - DISPONIBLE PARA TODOS LOS USUARIOS
    @Transactional(readOnly = true)
    @Cacheable(value = "busquedaProductos", key = "#productoBusquedaDTO.termino + '-' + #productoBusquedaDTO.categoriaId + '-' + #productoBusquedaDTO.pagina + '-' + #productoBusquedaDTO.tamanio")
    public PaginacionResponseDTO<ProductoResponseDTO> buscarProductosAvanzado(ProductoBusquedaDTO productoBusquedaDTO) {
        Pageable pageable = PageRequest.of(productoBusquedaDTO.getPagina(), productoBusquedaDTO.getTamanio());
        Page<ProductoEntity> paginaProductos;

        Optional<String> terminoOpt = Optional.ofNullable(productoBusquedaDTO.getTermino())
                .filter(t -> !t.trim().isEmpty())
                .map(t -> t.trim());
        
        Optional<UUID> categoriaIdOpt = Optional.ofNullable(productoBusquedaDTO.getCategoriaId());

        if (terminoOpt.isPresent() && categoriaIdOpt.isPresent()) {
            paginaProductos = productoRepository.buscarPorCategoriaYNombre(
                categoriaIdOpt.get(),
                terminoOpt.get(),
                pageable
            );
        } else if (terminoOpt.isPresent()) {
            paginaProductos = productoRepository.buscarPorTermino(terminoOpt.get(), pageable);

        } else if (categoriaIdOpt.isPresent()) {
            paginaProductos = productoRepository.buscarPorCategoria(categoriaIdOpt.get(), pageable);

        } else {
            paginaProductos = productoRepository.findAllWithDetails(pageable);
        }
        return convertirAPaginacionResponse(paginaProductos);
    }


    // OBTENER PRODUCTO POR ID - DISPONIBLE PARA TODOS LOS USUARIOS
    @Transactional(readOnly = true)
    @Cacheable(value = "productoPorId", key = "#idProducto")
    public ProductoResponseDTO getProductoById(UUID idProducto) {
        ProductoEntity producto = productoRepository.findByIdWithDetails(idProducto)
                .orElseThrow(() -> new BusinessRuleException("Producto no encontrado por este ID."));
        return convertirAResponseDTO(producto);
    }

    // CREAR PRODUCTO - SOLO ADMINISTRADORES
    @CacheEvict(value = {"productos", "busquedaProductos"}, allEntries = true)
    @Transactional
    public ProductoResponseDTO crearProducto(ProductoCreateDTO productoCreateDTO, UUID idAdmin) {
        adminValidationService.validarAdmin(idAdmin);

        if (productoRepository.existsByNombreProducto(productoCreateDTO.getNombreProducto())) {
            throw new BusinessRuleException("Ya existe un producto con este nombre");
        }

        CategoriaEntity categoria = categoriaRepository.findById(productoCreateDTO.getIdCategoria())
                .orElseThrow(() -> new BusinessRuleException("Categoría no encontrada"));
        
        if (productoCreateDTO.getImagen() == null || productoCreateDTO.getImagen().isEmpty()) {
            throw new BusinessRuleException("La imagen del producto es obligatoria");
        }

        String imagenUrl = fileStorageService.storageFile(productoCreateDTO.getImagen());

        ProductoEntity producto = new ProductoEntity();
        producto.setNombreProducto(productoCreateDTO.getNombreProducto());
        producto.setDescripcionProducto(productoCreateDTO.getDescripcionProducto());
        producto.setPrecioProducto(productoCreateDTO.getPrecioProducto());
        producto.setStockProducto(productoCreateDTO.getStockProducto());
        producto.setUrlImagenProducto(imagenUrl);
        producto.setCategoria(categoria);
        producto.setUsuario(adminValidationService.validarAdmin(idAdmin));

        ProductoEntity productoGuardado = productoRepository.save(producto);
        return convertirAResponseDTO(productoGuardado);
    }

    // ACTUALIZAR PRODUCTO - SOLO ADMINISTRADORES
    @Caching(evict = {
        @CacheEvict(value = {"productos", "busquedaProductos"}, allEntries = true),
        @CacheEvict(value = "productoPorId", key = "#idProducto")
    })
    @Transactional
    public ProductoResponseDTO actualizarProducto(UUID idProducto, ProductoUpdateDTO productoUpdateDTO, UUID idAdmin) {
        adminValidationService.validarAdmin(idAdmin);

        ProductoEntity producto = productoRepository.findByIdWithDetails(idProducto)
                .orElseThrow(() -> new BusinessRuleException("Producto no encontrado con este ID"));
        producto.setPrecioProducto(productoUpdateDTO.getPrecioProducto());
        producto.setStockProducto(productoUpdateDTO.getStockProducto());
        
        if (productoUpdateDTO.getImagen() != null && !productoUpdateDTO.getImagen().isEmpty()) {
            fileStorageService.deleteFile(producto.getUrlImagenProducto());
            String nuevaImagenUrl = fileStorageService.storageFile(productoUpdateDTO.getImagen());
            producto.setUrlImagenProducto(nuevaImagenUrl);
        }
        
        ProductoEntity productoActualizado = productoRepository.save(producto);
        return convertirAResponseDTO(productoActualizado);
    }

    // ELIMINAR PRODUCTO - SOLO ADMINISTRADORES
    @Caching(evict = {
        @CacheEvict(value = {"productos", "busquedaProductos"}, allEntries = true),
        @CacheEvict(value = "productoPorId", key = "#idProducto")
    })
    @Transactional
    public void eliminarProducto(UUID idProducto, UUID idAdmin) {
        adminValidationService.validarAdmin(idAdmin);

        ProductoEntity producto = productoRepository.findByIdWithDetails(idProducto)
            .orElseThrow(() -> new BusinessRuleException("Producto no encontrado"));
        
        fileStorageService.deleteFile(producto.getUrlImagenProducto());
        productoRepository.delete(producto);
    }

    // METODO PARA CONVERTIR A RESPONSE EN LA ENTIDAD
    private ProductoResponseDTO convertirAResponseDTO(ProductoEntity producto) {
        return productoMapper.toResponse(producto);
    }

    //METDO PARA CONVERTIR A RESPONSE LAS CATEGORIAS DE LOS PRODUCTOS
    private ProductoCategoriaResponseDTO productoCategoriaConvertirAResponse(CategoriaEntity categoriaEntity) {
        return productoMapper.toCategoriaResponse(categoriaEntity);
    }

    // METODO PARA CONVERTIR PAGINA DE ENTIDADES A PAGINA DE DTOs
    private PaginacionResponseDTO<ProductoResponseDTO> convertirAPaginacionResponse(Page<ProductoEntity> paginaProductos) {
        return productoMapper.toPaginacionResponse(paginaProductos);
    }
}
