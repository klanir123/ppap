package com.msg.ppap.exchange.application.port.out;

import com.msg.ppap.exchange.domain.StepName;

/**
 * A dataspace operation failed.
 *
 * @see #step() step in which it failed
 * @see #retryable() whether a later attempt may succeed
 */
public class DataspaceException extends RuntimeException {

    private final StepName step;
    private final boolean retryable;
    private final Integer httpStatus;

    public DataspaceException(StepName step, String message, boolean retryable, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.step = step;
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }

    public DataspaceException(StepName step, String message) {
        this(step, message, true, null, null);
    }

    public StepName step() {
        return step;
    }

    public boolean retryable() {
        return retryable;
    }

    public Integer httpStatus() {
        return httpStatus;
    }
}
