package com.fesc.tiendaOnline.model.dto;

/**
 * Separates HTTP concerns (cookie/headers) from business logic in AuthService.
 * The accessToken goes in the Authorization header, the refreshToken goes in
 * an HttpOnly cookie, and the responseBody is serialized as JSON.
 */
public record AuthResult(
    String accessToken,
    String refreshToken,
    LoginResponseDTO responseBody
) {}
