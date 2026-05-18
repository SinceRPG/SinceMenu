package net.danh.sincemenu.requirement;

import net.danh.sincemenu.placeholder.PlaceholderResolver;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class PapiStringRequirement {

    private final PlaceholderResolver placeholders;

    public PapiStringRequirement(@NotNull PlaceholderResolver placeholders) {
        this.placeholders = placeholders;
    }

    public boolean test(@NotNull Player player, @NotNull String expression) {
        String resolved = placeholders.resolve(player, expression);
        String[] operators = {"!=", ">=", "<=", "==", "=", ">", "<"};
        for (String operator : operators) {
            int index = resolved.indexOf(operator);
            if (index <= 0) {
                continue;
            }
            String left = resolved.substring(0, index).trim();
            String right = resolved.substring(index + operator.length()).trim();
            return compare(left, right, operator);
        }
        return Boolean.parseBoolean(resolved);
    }

    private boolean compare(@NotNull String left, @NotNull String right, @NotNull String operator) {
        Double leftNumber = parseDouble(left);
        Double rightNumber = parseDouble(right);
        if (leftNumber != null && rightNumber != null) {
            return switch (operator) {
                case ">" -> leftNumber > rightNumber;
                case ">=" -> leftNumber >= rightNumber;
                case "<" -> leftNumber < rightNumber;
                case "<=" -> leftNumber <= rightNumber;
                case "!=" -> !leftNumber.equals(rightNumber);
                case "==", "=" -> leftNumber.equals(rightNumber);
                default -> false;
            };
        }
        return switch (operator) {
            case "!=" -> !left.equalsIgnoreCase(right);
            case "==", "=" -> left.equalsIgnoreCase(right);
            default -> false;
        };
    }

    private Double parseDouble(@NotNull String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
