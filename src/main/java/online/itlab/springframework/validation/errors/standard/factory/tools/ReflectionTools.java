package online.itlab.springframework.validation.errors.standard.factory.tools;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

// NOTE: Spring provides itw own implementation of reflection tools
//       import org.springframework.util.ReflectionUtils;
// TODO evaluate Spring implementation and it's usage
public class ReflectionTools implements IReflectionTools {

    /**
     *
     * @param rootClass
     * @param indexedJavaPath Dot separated path composed of java field names.
     *                        List: javaFieldName[indexPart], eg: loans[0].guarantors[1].firstName
     * @return
     */
    public String toJsonPath(final Class<?> rootClass, final String indexedJavaPath) {
        final String[] pathSegments = indexedJavaPath.split("\\.");
        final StringBuilder result = new StringBuilder();

        Class<?> current = rootClass;
        for (int i = 0; i < pathSegments.length; i++) {
            final String pathSegment = pathSegments[i];
            final PathSegment decomposedPathSegment = decompose(pathSegment);

            final Field field = findField(current, decomposedPathSegment.javaFieldName);
            final String jsonName = getJsonName(field);

            if (i > 0) {
                result.append(".");
            }
            result.append(jsonName);
            result.append(decomposedPathSegment.indexPart);

            // if a field is a collection we need to take the class of the actual element
            // TODO getNextSegmentClass ignores decomposedPathSegment.indexPart
            //      If a field is a collection and indexPart is empty String this is IllegalStateException
            //      Also if field is not a collection and indexPart is not empty this is IllegalStateException
            //      This should never happen as long as Jakarta Validation works correctly
            //      Consider handling this situation.
            current = getNextSegmentClass(field);
        }

        return result.toString();
    }

    /**
     * Returns a class to follow the indexed java path.
     * Result:
     * - actual class -> actual class
     * - collection -> parametrized class
     */
    private Class<?> getNextSegmentClass(final Field field) {
        final boolean isCollection = Collection.class.isAssignableFrom(field.getType());
        if (isCollection) {
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType parameterizedType) {
                Type argType = parameterizedType.getActualTypeArguments()[0];
                if (argType instanceof Class<?>) {
                    return (Class<?>) argType;
                }

                // TODO List of lists - still needs to be supported
                if (argType instanceof ParameterizedType pt) {
                    return (Class<?>) pt.getRawType();
                }
            }
        }
        final boolean isMap = Map.class.isAssignableFrom(field.getType());
        if (isMap) {
            // TODO temporal destructive behavior - replace with proper handling
            throw new UnsupportedOperationException("Cannot follow Maps");
        }
        return field.getType();
    }

    public Field findField(final Class<?> type, final String javaName) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(field -> field.getName().equals(javaName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Field '%s' not found in %s".formatted(javaName, type.getName())
            ));
    }

    private PathSegment decompose(final String pathSegment) {
        int idx = pathSegment.indexOf('[');
        if (idx == -1) {
            return new PathSegment(pathSegment, "");
        }
        return new PathSegment(
            pathSegment.substring(0, idx),
            pathSegment.substring(idx)
        );
    }

    private String getJsonName(final Field field) {
        final JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
        return jsonProperty != null
            ? jsonProperty.value()
            : field.getName();
    }

    private record PathSegment(
        String javaFieldName,
        String indexPart
    ) {}
}
