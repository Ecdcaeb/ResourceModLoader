package rml.compiler.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import rml.compiler.CompileException;
import rml.compiler.Ids;
import rml.compiler.SourceResource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** JSON validation shared by the target-specific lowerers. */
final class RuleJson {
    private RuleJson() { }

    static CompileException error(SourceResource source, String code, String message) {
        return new CompileException(code, source.getPath(), 0, message);
    }

    static JsonObject object(JsonElement value, SourceResource source, String field) {
        if (value == null || !value.isJsonObject()) throw error(source, "invalid_rule", field + " must be an object");
        return value.getAsJsonObject();
    }

    static JsonArray array(JsonElement value, SourceResource source, String field) {
        if (value == null || !value.isJsonArray()) throw error(source, "invalid_rule", field + " must be an array");
        return value.getAsJsonArray();
    }

    static String string(JsonElement value, SourceResource source, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw error(source, "invalid_rule", field + " must be a string");
        }
        return value.getAsString();
    }

    static String id(JsonElement value, SourceResource source, String field) {
        String text = string(value, source, field);
        try { return Ids.parse(text); }
        catch (IllegalArgumentException e) { throw error(source, "invalid_identifier", field + ": " + e.getMessage()); }
    }

    static int integer(JsonElement value, SourceResource source, String field, int minimum, int maximum) {
        double number = number(value, source, field);
        if (number != Math.rint(number) || number < minimum || number > maximum) {
            throw error(source, "invalid_rule", field + " must be an integer in " + minimum + ".." + maximum);
        }
        return (int) number;
    }

    static double number(JsonElement value, SourceResource source, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw error(source, "invalid_rule", field + " must be a number");
        }
        double number = value.getAsDouble();
        if (Double.isNaN(number) || Double.isInfinite(number)) throw error(source, "invalid_rule", field + " must be finite");
        return number;
    }

    static boolean bool(JsonElement value, SourceResource source, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw error(source, "invalid_rule", field + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    static void fields(JsonObject object, SourceResource source, String... allowed) {
        Set<String> names = new HashSet<>(Arrays.asList(allowed));
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!names.contains(entry.getKey())) throw error(source, "unsupported_field", "Unsupported field: " + entry.getKey());
            if (entry.getValue().isJsonNull()) throw error(source, "invalid_rule", entry.getKey() + " must not be null");
        }
    }

    static JsonElement copy(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) result.add(entry.getKey(), copy(entry.getValue()));
            return result;
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement entry : value.getAsJsonArray()) result.add(copy(entry));
            return result;
        }
        return value; // Gson primitives and null are immutable.
    }

    static String type(JsonObject value, String field, SourceResource source) {
        return id(value.get(field), source, field);
    }

    /** JSON-compatible quoted strings are also valid SNBT quoted strings. */
    static String quote(String text) { return new JsonPrimitive(text).toString(); }

    static String snbt(JsonElement value, SourceResource source) {
        if (value.isJsonObject()) {
            StringBuilder result = new StringBuilder("{");
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                if (result.length() > 1) result.append(',');
                result.append(quote(entry.getKey())).append(':').append(snbt(entry.getValue(), source));
            }
            return result.append('}').toString();
        }
        if (value.isJsonArray()) {
            StringBuilder result = new StringBuilder("[");
            String kind = null;
            for (JsonElement entry : value.getAsJsonArray()) {
                String current = nbtKind(entry, source);
                if (kind != null && !kind.equals(current)) throw error(source, "unsupported_component", "NBT lists must have homogeneous element types");
                kind = current;
                if (result.length() > 1) result.append(',');
                result.append(snbt(entry, source));
            }
            return result.append(']').toString();
        }
        if (value.isJsonNull()) throw error(source, "invalid_rule", "NBT does not support null");
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isString()) return quote(primitive.getAsString());
        if (primitive.isBoolean()) return primitive.getAsBoolean() ? "1b" : "0b";
        number(value, source, "NBT number");
        BigDecimal number = primitive.getAsBigDecimal();
        if (number.stripTrailingZeros().scale() > 0) return number.toPlainString() + "d";
        try { return Integer.toString(number.intValueExact()); }
        catch (ArithmeticException e) {
            try { return Long.toString(number.longValueExact()) + "L"; }
            catch (ArithmeticException ignored) { throw error(source, "invalid_rule", "NBT integer is outside signed 64-bit range"); }
        }
    }
    private static String nbtKind(JsonElement value, SourceResource source) {
        if (value.isJsonObject()) return "compound";
        if (value.isJsonArray()) return "list";
        if (value.isJsonNull()) throw error(source, "invalid_rule", "NBT does not support null");
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isString()) return "string";
        if (primitive.isBoolean()) return "byte";
        number(value, source, "NBT number");
        BigDecimal numeric = primitive.getAsBigDecimal();
        if (numeric.stripTrailingZeros().scale() > 0) return "double";
        return numeric.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) >= 0
                && numeric.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0 ? "int" : "long";
    }
}
