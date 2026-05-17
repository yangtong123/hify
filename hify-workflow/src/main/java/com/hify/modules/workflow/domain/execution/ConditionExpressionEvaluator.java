package com.hify.modules.workflow.domain.execution;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ConditionExpressionEvaluator {

    private static final List<String> OPERATORS = List.of(">=", "<=", "==", "!=", ">", "<");

    public boolean evaluate(String expression, WorkflowContext context) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        String trimmed = expression.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        for (String operator : OPERATORS) {
            int index = trimmed.indexOf(operator);
            if (index > 0) {
                Object left = context.resolve(trimmed.substring(0, index).trim());
                Object right = literalOrValue(trimmed.substring(index + operator.length()).trim(), context);
                return compare(left, right, operator);
            }
        }
        Object value = context.resolve(trimmed);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0D;
        }
        return value != null && !String.valueOf(value).isBlank();
    }

    private Object literalOrValue(String expression, WorkflowContext context) {
        if ((expression.startsWith("'") && expression.endsWith("'"))
                || (expression.startsWith("\"") && expression.endsWith("\""))) {
            return expression.substring(1, expression.length() - 1);
        }
        if ("null".equalsIgnoreCase(expression)) {
            return null;
        }
        if ("true".equalsIgnoreCase(expression) || "false".equalsIgnoreCase(expression)) {
            return Boolean.parseBoolean(expression);
        }
        try {
            return new BigDecimal(expression);
        } catch (NumberFormatException ignored) {
            Object resolved = context.resolve(expression);
            return resolved != null ? resolved : expression;
        }
    }

    private boolean compare(Object left, Object right, String operator) {
        if ("==".equals(operator)) {
            return left == null ? right == null : String.valueOf(left).equals(String.valueOf(right));
        }
        if ("!=".equals(operator)) {
            return left == null ? right != null : !String.valueOf(left).equals(String.valueOf(right));
        }
        BigDecimal leftNumber = toNumber(left);
        BigDecimal rightNumber = toNumber(right);
        if (leftNumber == null || rightNumber == null) {
            return false;
        }
        int result = leftNumber.compareTo(rightNumber);
        return switch (operator) {
            case ">" -> result > 0;
            case "<" -> result < 0;
            case ">=" -> result >= 0;
            case "<=" -> result <= 0;
            default -> false;
        };
    }

    private BigDecimal toNumber(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
