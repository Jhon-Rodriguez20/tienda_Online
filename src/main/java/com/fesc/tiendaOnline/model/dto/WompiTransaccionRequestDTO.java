package com.fesc.tiendaOnline.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WompiTransaccionRequestDTO {

    @JsonProperty("acceptance_token")
    private String acceptanceToken;

    @JsonProperty("amount_in_cents")
    private long amount_in_cents;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("customer_email")
    private String customer_email;

    @JsonProperty("reference")
    private String reference;

    @JsonProperty("payment_method_type")
    private String payment_method_type;

    @JsonProperty("payment_method")
    private PaymentMethod payment_method;

    /**
     * Firma de integridad para la API de transacciones de Wompi.
     * En la API REST se envía como string plano (el hash SHA-256).
     * El formato objeto {"integrity":"..."} aplica solo al Widget/Checkout Web.
     */
    @JsonProperty("signature")
    private String signature;

    @Data
    public static class PaymentMethod {

        @JsonProperty("type")
        private String type;

        /** Nequi: número de celular de 10 dígitos registrado en Nequi */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("phone_number")
        private String phone_number;

        /** CARD: token de la tarjeta tokenizada */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("token")
        private String token;

        /** CARD: número de cuotas */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("installments")
        private Integer installments;

        /** BANCOLOMBIA_TRANSFER: tipo de persona, solo "PERSON" disponible actualmente */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("user_type")
        private String user_type;

        /** BANCOLOMBIA_TRANSFER: descripción del pago, máximo 64 caracteres */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("payment_description")
        private String payment_description;
    }
}
