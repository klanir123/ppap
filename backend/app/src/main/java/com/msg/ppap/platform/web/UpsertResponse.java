package com.msg.ppap.platform.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents an idempotent upsert: {@code 200} if updated or unchanged, {@code 201} with {@code Location} if created
 * (OpenAPI only).
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UpsertResponse {}
