package com.fesc.tiendaOnline.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final RestClient restClient;

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    @Value("${brevo.sender.name}")
    private String senderName;

    public EmailService(RestClient.Builder restClientBuilder,
                        @Value("${brevo.api.url}") String apiUrl) {
        this.restClient = restClientBuilder
                .baseUrl(apiUrl)
                .build();
    }

    @Async("emailExecutor")
    public void enviarCodigoVerificacion(String email, String codigo) {
        String asunto = "Codigo de Verificacion - Tienda Online";
        String cuerpo = "Hola,\n\n"
                + "Tu codigo de verificacion es: " + codigo + "\n\n"
                + "Este codigo expirara en 15 minutos.\n\n"
                + "Si no solicitaste este codigo, ignora este mensaje.\n\n"
                + "Saludos,\nEquipo Tienda Online";
        enviar(email, asunto, cuerpo);
    }

    @Async("emailExecutor")
    public void enviarCodigoRecuperacion(String email, String codigo) {
        String asunto = "Recuperacion de Contrasena - Tienda Online";
        String cuerpo = "Hola,\n\n"
                + "Has solicitado recuperar tu contrasena.\n\n"
                + "Tu codigo de verificacion es: " + codigo + "\n\n"
                + "Este codigo expirara en 15 minutos.\n\n"
                + "Si no solicitaste este cambio, ignora este mensaje.\n\n"
                + "Saludos,\nEquipo Tienda Online";
        enviar(email, asunto, cuerpo);
    }

    @Async("emailExecutor")
    public void enviarConfirmacionCambioContrasena(String email) {
        String asunto = "Contrasena Actualizada - Tienda Online";
        String cuerpo = "Hola,\n\n"
                + "Tu contrasena ha sido actualizada exitosamente.\n\n"
                + "Si no realizaste este cambio, contacta inmediatamente con soporte.\n\n"
                + "Saludos,\nEquipo Tienda Online";
        enviar(email, asunto, cuerpo);
    }

    @Async("emailExecutor")
    public void enviarConfirmacionCancelacionCuenta(String email) {
        String asunto = "Cuenta Cancelada - Tienda Online";
        String cuerpo = "Hola,\n\n"
                + "Tu cuenta ha sido cancelada exitosamente.\n\n"
                + "Sentimos verte partir. Si fue un error, contacta con soporte.\n\n"
                + "Saludos,\nEquipo Tienda Online";
        enviar(email, asunto, cuerpo);
    }

    /**
     * Realiza la llamada HTTP a la API transaccional de Brevo.
     * Documentación: https://developers.brevo.com/reference/sendtransacemail
     */
    private void enviar(String destinatario, String asunto, String cuerpo) {
        try {
            Map<String, Object> payload = Map.of(
                    "sender", Map.of("name", senderName, "email", senderEmail),
                    "to", List.of(Map.of("email", destinatario)),
                    "subject", asunto,
                    "textContent", cuerpo
            );

            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("api-key", apiKey)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            logger.info("Correo enviado via Brevo API a {}", destinatario);

        } catch (Exception e) {
            logger.error("Error al enviar correo via Brevo API a {}: {}", destinatario, e.getMessage());
        }
    }
}
