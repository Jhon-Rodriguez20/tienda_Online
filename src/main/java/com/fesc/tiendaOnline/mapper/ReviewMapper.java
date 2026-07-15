package com.fesc.tiendaOnline.mapper;

import org.springframework.stereotype.Component;

import com.fesc.tiendaOnline.model.dto.ReviewResponseDTO;
import com.fesc.tiendaOnline.model.entity.ReviewEntity;

@Component
public class ReviewMapper {

    public ReviewResponseDTO toResponse(ReviewEntity review) {
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
