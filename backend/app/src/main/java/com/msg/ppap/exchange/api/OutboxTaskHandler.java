package com.msg.ppap.exchange.api;

import java.util.UUID;

/**
 * Extension point: a local task queued in the transactional outbox (same transaction as the case change) and executed
 * by the outbox worker in order with the partner messages of the same exchange id, with the outbox retry/backoff –
 * e.g. the DTR registration ({@code TWIN_REGISTRATION}) that must precede the {@code PPAP_SUBMISSION_AVAILABLE}
 * notification. Implementations must be idempotent.
 */
public interface OutboxTaskHandler {

    /** Record type handled, e.g. {@code TWIN_REGISTRATION}. */
    String taskType();

    /**
     * Executes the task.
     *
     * @param recorder records steps in the exchange record (cockpit)
     * @throws TaskFailure if the task failed (retried unless {@link TaskFailure#retryable()} is {@code false})
     */
    void execute(OutboxTask task, StepRecorder recorder);

    /**
     * A queued task.
     *
     * @param attempt execution attempt (1, 2, …)
     */
    record OutboxTask(UUID id, UUID caseId, UUID exchangeId, String partnerBpnl, int attempt) {}

    /** Records a step of the task. */
    @FunctionalInterface
    interface StepRecorder {

        /**
         * @param step step name of the exchange record, e.g. {@code TWIN_REGISTRATION}
         * @param ok outcome
         * @param durationMs duration
         * @param details JSON-compatible details
         */
        void step(String step, boolean ok, long durationMs, java.util.Map<String, Object> details);
    }

    /** Failure of a task. */
    class TaskFailure extends RuntimeException {

        private final boolean retryable;

        public TaskFailure(String message, boolean retryable, Throwable cause) {
            super(message, cause);
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }
    }
}
