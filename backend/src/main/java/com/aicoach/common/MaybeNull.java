package com.aicoach.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a response field that can be null. Every other response field is published as required in the
 * OpenAPI spec (see {@code OpenApiConfig}), so generated client types only allow null where it can happen.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD})
public @interface MaybeNull {
}
