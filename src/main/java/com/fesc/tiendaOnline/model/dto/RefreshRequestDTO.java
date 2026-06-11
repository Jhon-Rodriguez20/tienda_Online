package com.fesc.tiendaOnline.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshRequestDTO {

    @NotBlank
    private String refreshToken;
}
