package com.fesc.tiendaOnline.model.dto;

/**
 * Wrapper genérico para resultados de operaciones idempotentes.
 *
 * @param data     el resultado de la operación
 * @param replayed true si la respuesta fue recuperada del IdempotencyStore (solicitud duplicada),
 *                 false si la operación se ejecutó por primera vez
 */
public record IdempotencyResult<T>(T data, boolean replayed) {}
