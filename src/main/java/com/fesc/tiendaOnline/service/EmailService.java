package com.fesc.tiendaOnline.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async("emailExecutor")
    public void enviarCodigoVerificacion(String email, String codigo) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Codigo de Verificacion - Tienda Online");
            message.setText("Hola,\n\n"
                    + "Tu codigo de verificacion es: " + codigo + "\n\n"
                    + "Este codigo expirara en 15 minutos.\n\n"
                    + "Si no solicitaste este codigo, ignora este mensaje.\n\n"
                    + "Saludos,\n"
                    + "Equipo Tienda Online");
            mailSender.send(message);
            logger.info("Correo de verificacion enviado a {}", email);

        } catch (Exception e) {
            logger.error("Error al enviar email a {}: {}", email, e.getMessage());
        }
    }

    @Async("emailExecutor")
    public void enviarCodigoRecuperacion(String email, String codigo) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Recuperacion de Contrasena - Tienda Online");
            message.setText("Hola,\n\n"
                    + "Has solicitado recuperar tu contrasena.\n\n"
                    + "Tu codigo de verificacion es: " + codigo + "\n\n"
                    + "Este codigo expirara en 15 minutos.\n\n"
                    + "Si no solicitaste este cambio, ignora este mensaje.\n\n"
                    + "Saludos,\n"
                    + "Equipo Tienda Online");

            mailSender.send(message);
            logger.info("Codigo de recuperacion enviado a: {}", email);

        } catch (Exception e) {
            logger.error("Error al enviar email de recuperacion a {}: {}", email, e.getMessage());
        }
    }

    @Async("emailExecutor")
    public void enviarConfirmacionCambioContrasena(String email) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Contrasena Actualizada - Tienda Online");
            message.setText("Hola,\n\n"
                    + "Tu contrasena ha sido actualizada exitosamente.\n\n"
                    + "Si no realizaste este cambio, contacta inmediatamente con soporte.\n\n"
                    + "Saludos,\n"
                    + "Equipo Tienda Online");

            mailSender.send(message);
            logger.info("Correo de confirmacion de cambio de contrasena enviado a: {}", email);

        } catch (Exception e) {
            logger.error("Error al enviar confirmacion a {}: {}", email, e.getMessage());
        }
    }

    @Async("emailExecutor")
    public void enviarConfirmacionCancelacionCuenta(String email) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Cuenta Cancelada - Tienda Online");
            message.setText("Hola,\n\n"
                    + "Tu cuenta ha sido cancelada exitosamente.\n\n"
                    + "Sentimos verte partir. Si fue un error, contacta con soporte.\n\n"
                    + "Saludos,\n"
                    + "Equipo Tienda Online");

            mailSender.send(message);
            logger.info("Correo de confirmacion de cancelacion enviado a: {}", email);

        } catch (Exception e) {
            logger.error("Error al enviar confirmacion de cancelacion a {}: {}", email, e.getMessage());
        }
    }
}
