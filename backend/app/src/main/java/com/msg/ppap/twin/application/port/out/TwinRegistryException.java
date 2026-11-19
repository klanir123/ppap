package com.msg.ppap.twin.application.port.out;

/**
 * A call to the Digital Twin Registry failed.
 *
 * @see #httpStatus() status of the answer, {@code null} if not reachable
 */
public class TwinRegistryException extends RuntimeException {

    private final Integer httpStatus;

    public TwinRegistryException(String message, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    /** Client errors (invalid descriptor, forbidden) do not heal by retrying. */
    public boolean retryable() {
        return httpStatus == null || httpStatus >= 500 || httpStatus == 401 || httpStatus == 408 || httpStatus == 429;
    }
}
