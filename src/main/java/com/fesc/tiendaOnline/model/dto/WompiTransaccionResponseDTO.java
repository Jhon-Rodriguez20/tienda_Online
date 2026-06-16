package com.fesc.tiendaOnline.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WompiTransaccionResponseDTO {

    @JsonProperty("data")
    private TransaccionData data;

    @Data
    public static class TransaccionData {

        @JsonProperty("id")
        private String id;

        @JsonProperty("status")
        private String status;

        @JsonProperty("reference")
        private String reference;

        @JsonProperty("amount_in_cents")
        private long amount_in_cents;

        @JsonProperty("payment_method_type")
        private String payment_method_type;

        @JsonProperty("redirect_url")
        private String redirect_url;

        /**
         * Objeto de método de pago. Contiene el campo "extra" con "async_payment_url"
         * para métodos como BANCOLOMBIA_TRANSFER.
         */
        @JsonProperty("payment_method")
        private PaymentMethodData payment_method;
    }

    @Data
    public static class PaymentMethodData {

        @JsonProperty("type")
        private String type;

        @JsonProperty("extra")
        private PaymentMethodExtra extra;
    }

    @Data
    public static class PaymentMethodExtra {

        /**
         * URL de redirección para completar el pago en métodos asincrónicos
         * (BANCOLOMBIA_TRANSFER, PSE, etc.)
         */
        @JsonProperty("async_payment_url")
        private String async_payment_url;
    }

    // ─── Métodos de conveniencia para mantener compatibilidad con el código existente ───
    public String getId() {
        return data != null ? data.getId() : null;
    }

    public String getStatus() {
        return data != null ? data.getStatus() : null;
    }

    public String getAsync_payment_url() {
        if (data == null || data.getPayment_method() == null
                || data.getPayment_method().getExtra() == null) {
            return null;
        }
        return data.getPayment_method().getExtra().getAsync_payment_url();
    }
}
