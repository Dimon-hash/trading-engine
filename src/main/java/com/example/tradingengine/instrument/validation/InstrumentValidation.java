package com.example.tradingengine.instrument.validation;

import com.example.tradingengine.model.Instrument;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class InstrumentValidation {

    private InstrumentValidation() {}

    public static void validatePrice(Instrument instrument, BigDecimal price) {
        if (price.signum() <= 0 || price.remainder(instrument.minStep()).signum() != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Цена должна быть положительной и кратной шагу");
        }
    }

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    @NotBlank
    public @interface RequiredSeccode {
        String message() default "{jakarta.validation.constraints.NotBlank.message}";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }
}
