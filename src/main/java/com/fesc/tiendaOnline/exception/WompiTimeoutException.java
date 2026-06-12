package com.fesc.tiendaOnline.exception;

public class WompiTimeoutException extends RuntimeException {

    public WompiTimeoutException(String message) {
        super(message);
    }

    public WompiTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
