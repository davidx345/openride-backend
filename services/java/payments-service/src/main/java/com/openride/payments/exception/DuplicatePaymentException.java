qpackage com.openride.payments.exception;

/**
 * Exception thrown when a duplicate payment request is detected.
 * This occurs when the same idempotency key is used more than once.
 */
public class DuplicatePaymentException extends RuntimeException {

    public DuplicatePaymentException(String message) {
        super(message);
    }

    public DuplicatePaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
