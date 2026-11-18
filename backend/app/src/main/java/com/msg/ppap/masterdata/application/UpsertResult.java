package com.msg.ppap.masterdata.application;

/**
 * Result of an upsert by business key.
 *
 * @param value persisted state
 * @param action what happened
 * @param <T> aggregate or view type
 */
public record UpsertResult<T>(T value, ChangeAction action) {

    /** Maps the value, keeping the action. */
    public <R> UpsertResult<R> map(java.util.function.Function<T, R> mapper) {
        return new UpsertResult<>(mapper.apply(value), action);
    }
}
