package online.itlab.springframework.validation.errors.standard.factory;

import java.lang.reflect.Field;

public interface IReflectionTools {
    String toJsonPath(Class<?> rootClass, String javaPath);
    Field findField(Class<?> clazz, String javaName);
}
