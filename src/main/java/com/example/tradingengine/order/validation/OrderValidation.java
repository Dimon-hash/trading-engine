package com.example.tradingengine.order.validation;

import com.example.tradingengine.order.ChangeOrderPriceRequest;
import com.example.tradingengine.order.CreateOrderRequest;
import java.math.BigDecimal;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;
import org.springframework.web.server.ResponseStatusException;

public final class OrderValidation implements Validator {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    public static void validateIdempotencyKey(String key) {
        if (key != null && !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный Idempotency-Key");
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type == CreateOrderRequest.class || type == ChangeOrderPriceRequest.class;
    }

    @Override
    public void validate(Object target, Errors errors) {
        if (target instanceof CreateOrderRequest request) {
            requirePositive(request.accountId(), "accountId", errors);
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "seccode", "NotBlank", "must not be blank");
            ValidationUtils.rejectIfEmpty(errors, "side", "NotNull", "must not be null");
            validatePrice(request.price(), errors);
            requirePositive(request.quantity(), "quantity", errors);
        } else if (target instanceof ChangeOrderPriceRequest request) {
            validatePrice(request.price(), errors);
        }
    }

    private static void requirePositive(long value, String field, Errors errors) {
        if (value <= 0) {
            errors.rejectValue(field, "Positive", "must be greater than 0");
        }
    }

    private static void validatePrice(BigDecimal price, Errors errors) {
        if (price == null) {
            errors.rejectValue("price", "NotNull", "must not be null");
        } else if (price.signum() <= 0) {
            errors.rejectValue("price", "Positive", "must be greater than 0");
        }
    }
}
