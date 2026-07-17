package com.kyra.common.result;

import java.util.Objects;
import java.util.function.Function;

/**
 * Explicit success/failure for domain operations whose failure is a normal
 * outcome (validation, insufficient balance, …). Exceptions remain for bugs
 * and infrastructure faults only.
 */
public sealed interface Result<T> permits Result.Ok, Result.Err {

    record Ok<T>(T value) implements Result<T> {
        public Ok {
            Objects.requireNonNull(value, "value");
        }
    }

    record Err<T>(ErrorCode error) implements Result<T> {
        public Err {
            Objects.requireNonNull(error, "error");
        }
    }

    static <T> Result<T> ok(T value) {
        return new Ok<>(value);
    }

    static <T> Result<T> err(ErrorCode error) {
        return new Err<>(error);
    }

    default boolean isOk() {
        return this instanceof Ok<T>;
    }

    default T orElseThrow() {
        return switch (this) {
            case Ok<T> ok -> ok.value();
            case Err<T> err -> throw new IllegalStateException("result is error: " + err.error());
        };
    }

    default <U> Result<U> map(Function<T, U> fn) {
        return switch (this) {
            case Ok<T> ok -> Result.ok(fn.apply(ok.value()));
            case Err<T> err -> Result.err(err.error());
        };
    }
}
