package com.msg.ppap.exchange.application;

/**
 * Failure of a pull job.
 *
 * @see Outcome
 */
public class PullFailure extends RuntimeException {

    /** What happens with the job. */
    public enum Outcome {
        /** Retried with backoff ({@code PENDING}), {@code FAILED} after the last attempt. */
        RETRY,
        /** Content rejected (hash, signature, state): {@code REJECTED}, manual retry possible. */
        REJECT,
        /** Given up: {@code FAILED}, manual retry possible. */
        FAIL
    }

    private final String code;
    private final Outcome outcome;

    public PullFailure(String code, String message, Outcome outcome, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.outcome = outcome;
    }

    public PullFailure(String code, String message, Outcome outcome) {
        this(code, message, outcome, null);
    }

    public String code() {
        return code;
    }

    public Outcome outcome() {
        return outcome;
    }
}
