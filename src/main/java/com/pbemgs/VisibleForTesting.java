package com.pbemgs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a method, field, or class has package-private visibility
 * solely for testing purposes.
 */
@Retention(RetentionPolicy.CLASS)  // Exists in class files but not at runtime
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface VisibleForTesting {
}