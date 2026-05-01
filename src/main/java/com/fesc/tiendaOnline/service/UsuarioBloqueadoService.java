package com.fesc.tiendaOnline.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fesc.tiendaOnline.model.entity.UsuarioEntity;
import com.fesc.tiendaOnline.repository.UsuarioRepository;

@Service
public class UsuarioBloqueadoService {
    
    private final UsuarioRepository usuarioRepository;
    
    public UsuarioBloqueadoService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void guardarBloqueo(UsuarioEntity usuario, int minutosBloqueo) {
        LocalDateTime bloqueoHasta = LocalDateTime.now().plusMinutes(minutosBloqueo);
        usuario.setBloqueadoHasta(bloqueoHasta);
        usuario.setIntentosEnvioCodigoVerificacion(0);
        usuarioRepository.saveAndFlush(usuario);
    }
}
