package com.homebuddy.auth.account;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * Validates the UTF-8 byte length of a string. BCrypt silently ignores bytes beyond 72, so password
 * limits are expressed in bytes, not characters.
 */
@Constraint(validatedBy = ByteLength.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@interface ByteLength {

    String message() default "Value must be between {min} and {max} bytes";

    int min() default 0;

    int max() default Integer.MAX_VALUE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ByteLength, String> {

        private int min;
        private int max;

        @Override
        public void initialize(ByteLength constraint) {
            this.min = constraint.min();
            this.max = constraint.max();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true; // @NotBlank reports missing values.
            }
            int length = value.getBytes(StandardCharsets.UTF_8).length;
            return length >= min && length <= max;
        }
    }
}
