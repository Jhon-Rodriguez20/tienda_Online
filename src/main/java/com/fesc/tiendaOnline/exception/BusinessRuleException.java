package com.fesc.tiendaOnline.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "ERROR_NEGOCIO");
    }

    public BusinessRuleException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_REQUEST, "ERROR_NEGOCIO");
    }
}
