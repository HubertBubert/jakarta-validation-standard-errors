package online.itlab.springframework.validation.errors.standard.factory.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import online.itlab.springframework.validation.errors.standard.factory.domain.IValidationPathFactory;
import online.itlab.springframework.validation.errors.standard.factory.domain.IValidationPathFactory.ValidationPath;

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

    private final IValidationPathFactory validationPathFactory;

    public ReflectionTools(final IValidationPathFactory validationPathFactory) {
        this.validationPathFactory = validationPathFactory;
    }

    /**
     *
     * @param rootClass
     * @param indexedJavaPath Dot separated path composed of java field names.
     *                        List: javaFieldName[indexPart], eg: loans[0].guarantors[1].firstName
     * @return
     */
    public String toJsonPath(final Class<?> rootClass, final String indexedJavaPath) {
        final String[] indexedPathSegments = indexedJavaPath.split("\\.");
        final StringBuilder result = new StringBuilder();

        Class<?> current = rootClass;
        for (int i = 0; i < indexedPathSegments.length; i++) {
            final String indexedPathSegment = indexedPathSegments[i];
            final ValidationPath validationPath = validationPathFactory.create(indexedPathSegment);

            final Field field = findField(current, validationPath.javaFieldName());
            final String jsonName = getJsonName(field);

            if (i > 0) {
                result.append(".");
            }
            result.append(jsonName);
            result.append(validationPath.indexPart());

            // if a field is a collection we need to take the class of the actual element
            // TODO getNextSegmentClass ignores decomposedPathSegment.indexPart
            //      If a field is a collection and indexPart is empty String this is IllegalStateException
            //      Also if field is not a collection and indexPart is not empty this is IllegalStateException
            //      This should never happen as long as Jakarta Validation works correctly
            //      Consider handling this situation.
            current = getNextSegmentClass(field.getGenericType());
        }
        return result.toString();
    }

    /**
     * For collections returns stored types
     */
    private Class getNextSegmentClass(final Type type) {
        if (type instanceof Class) {
            return (Class) type;
        }
        if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class) {
                if (Collection.class.isAssignableFrom((Class<?>) rawType)) {
                    return getNextSegmentClass(((ParameterizedType) type).getActualTypeArguments()[0]);
                } else if (rawType == Map.class) {
                    return getNextSegmentClass(((ParameterizedType) type).getActualTypeArguments()[1]);
                }
                return (Class) rawType;
            }  else {
                throw new IllegalStateException("Unexpected ParameterizedType type: " + rawType);
            }
        }
        throw new IllegalStateException("Unexpected type: " + type);
    }

    public Field findField(final Class<?> type, final String javaFieldName) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(field -> field.getName().equals(javaFieldName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Field '%s' not found in %s".formatted(javaFieldName, type.getName())
            ));
    }

    private String getJsonName(final Field field) {
        final JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
        return jsonProperty != null
            ? jsonProperty.value()
            : field.getName();
    }
}
