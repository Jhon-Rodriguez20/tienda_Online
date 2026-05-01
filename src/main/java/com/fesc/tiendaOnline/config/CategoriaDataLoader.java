package com.fesc.tiendaOnline.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.fesc.tiendaOnline.model.entity.CategoriaEntity;
import com.fesc.tiendaOnline.repository.CategoriaRepository;

@Component
public class CategoriaDataLoader implements CommandLineRunner {
    
    private final CategoriaRepository categoriaRepository;
    
    public CategoriaDataLoader(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }
    
    @Override
    public void run(String... args) throws Exception {
        crearCategoriaSiNoExiste("Electrónicos", "Productos electrónicos como celulares, computadoras, tablets");
        crearCategoriaSiNoExiste("Ropa", "Prendas de vestir para hombre, mujer y niños");
        crearCategoriaSiNoExiste("Hogar", "Productos para el hogar y decoración");
        crearCategoriaSiNoExiste("Deportes", "Equipamiento y accesorios deportivos");
        crearCategoriaSiNoExiste("Libros", "Libros y material educativo");
        crearCategoriaSiNoExiste("Juguetes", "Juguetes y juegos para niños");
        crearCategoriaSiNoExiste("Salud", "Productos para el cuidado personal y salud");
        crearCategoriaSiNoExiste("Alimentos", "Productos alimenticios y bebidas");
    }
    
    private void crearCategoriaSiNoExiste(String nombre, String descripcion) {
        if (categoriaRepository.findByNombreCategoria(nombre).isEmpty()) {
            CategoriaEntity categoria = new CategoriaEntity();
            categoria.setNombreCategoria(nombre);
            categoria.setDescripcionCategoria(descripcion);
            categoriaRepository.save(categoria);
        }
    }
}
