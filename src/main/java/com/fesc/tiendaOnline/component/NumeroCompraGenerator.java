package com.fesc.tiendaOnline.component;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class NumeroCompraGenerator {

    private static final SecureRandom random = new SecureRandom();

    public String generarNumeroCompra() {
        return String.format("%06d", random.nextInt(900000) + 100000);
    }
}
