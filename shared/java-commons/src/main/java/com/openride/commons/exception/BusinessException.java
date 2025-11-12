package com.openride.commons.exception;

/**
 * Base exception for business logic errors.
 * Use this for expected errors that represent business rule violations.
 */
public class BusinessException extends RuntimeException {
    private final String errorCode;
    private final Object[] args;

    /**
     * Creates a new business exception.
     *
     * @param errorCode The error code for this business exception
     * @param message   The error message
     */
    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
    }

    /**
     * Creates a new business exception with arguments.
     *
     * @param errorCode The error code for this business exception
     * @param message   The error message
     * @param args      Arguments for message formatting
     */
    public BusinessException(String errorCode, String message, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args;
    }

    /**
     * Creates a new business exception with a cause.
     *
     * @param errorCode The error code for this business exception
     * @param message   The error message
     * @param cause     The underlying cause
     */
    public BusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = null;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object[] getArgs() {
        return args;
    }
}
