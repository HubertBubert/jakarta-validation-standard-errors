package online.itlab.springframework.validation.errors.standard.factory.tools;

import online.itlab.springframework.validation.errors.standard.factory.domain.types.In;
import org.springframework.web.context.request.WebRequest;

import java.lang.reflect.Field;

public interface IWebRequestTools {
    In resolveSource(WebRequest webRequest, Field field, String requestName);
}
