package online.itlab.springframework.validation.errors.standard.factory;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.reflect.Field;
import java.util.Arrays;

public class ReflectionTools implements IReflectionTools {

    public String toJsonPath(final Class<?> rootClass, final String javaPath) {
        final String[] parts = javaPath.split("\\.");
        final StringBuilder result = new StringBuilder();

        Class<?> current = rootClass;
        for (int i = 0; i < parts.length; i++) {
            final String part = parts[i];

            final Field field = findField(current, part);
            final String jsonName = getJsonName(field);

            if (i > 0) {
                result.append(".");
            }
            result.append(jsonName);

            current = field.getType();
        }

        return result.toString();
    }

    private static Field findField(final Class<?> type, final String javaName) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(field -> field.getName().equals(javaName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Field '%s' not found in %s".formatted(javaName, type.getName())
            ));
    }

    private static String getJsonName(final Field field) {
        final JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
        return jsonProperty != null
            ? jsonProperty.value()
            : field.getName();
    }
}
