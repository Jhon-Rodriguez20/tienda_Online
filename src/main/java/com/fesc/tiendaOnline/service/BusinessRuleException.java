package com.fesc.tiendaOnline.service;

import org.springframework.http.HttpStatus;

import com.fesc.tiendaOnline.exception.ApiException;

public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "ERROR_NEGOCIO");
    }

    public BusinessRuleException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_REQUEST, "ERROR_NEGOCIO");
    }
}
