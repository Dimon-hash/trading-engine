package com.example.tradingengine.order.validation;

import com.example.tradingengine.order.ChangeOrderPriceRequest;
import com.example.tradingengine.order.CreateOrderRequest;
import java.math.BigDecimal;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

public final class OrderValidation implements Validator {

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
