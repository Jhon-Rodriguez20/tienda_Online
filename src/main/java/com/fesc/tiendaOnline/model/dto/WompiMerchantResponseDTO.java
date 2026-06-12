package com.fesc.tiendaOnline.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class WompiMerchantResponseDTO {

    private DataMerchant data;

    @Data
    public static class DataMerchant {

        @JsonProperty("presigned_acceptance")
        private PresignedAcceptance presignedAcceptance;
    }

    @Data
    public static class PresignedAcceptance {

        @JsonProperty("acceptance_token")
        private String acceptanceToken;
    }
}