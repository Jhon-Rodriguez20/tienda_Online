package com.fesc.tiendaOnline.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.fesc.tiendaOnline.model.entity.MetodoPagoCompraEntity;
import com.fesc.tiendaOnline.repository.MetodoPagoRepository;

@Component
public class MetodoPagoDataLoader implements CommandLineRunner {

    private final MetodoPagoRepository metodoPagoRepository;
    
    public MetodoPagoDataLoader(MetodoPagoRepository metodoPagoRepository) {
        this.metodoPagoRepository = metodoPagoRepository;
    }
    
    @Override
    public void run(String... args) throws Exception {
        crearMetodoPagoSiNoExiste("Bancolombia");
        crearMetodoPagoSiNoExiste("Nequi");
        crearMetodoPagoSiNoExiste("Daviplata");
        crearMetodoPagoSiNoExiste("Tarjeta Débito o Crédito");
    }
    
    private void crearMetodoPagoSiNoExiste(String nombre) {
        if (metodoPagoRepository.findByMetodoPago(nombre).isEmpty()) {
            MetodoPagoCompraEntity metodoPago = new MetodoPagoCompraEntity();
            metodoPago.setMetodoPago(nombre);
            metodoPagoRepository.save(metodoPago);
        }
    }
}
