package com.openride.commons.exception;

/**
 * Base exception for technical/system errors.
 * Use this for unexpected errors like database failures, network issues, etc.
 */
public class TechnicalException extends RuntimeException {
    private final String errorCode;

    /**
     * Creates a new technical exception.
     *
     * @param errorCode The error code for this technical exception
     * @param message   The error message
     */
    public TechnicalException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Creates a new technical exception with a cause.
     *
     * @param errorCode The error code for this technical exception
     * @param message   The error message
     * @param cause     The underlying cause
     */
    public TechnicalException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
