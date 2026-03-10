package online.itlab.springframework.validation.errors.standard.factory.tools;

import org.springframework.web.context.request.WebRequest;

import java.lang.reflect.Field;

public interface IWebRequestTools {
    String resolveSource(WebRequest webRequest, Field field, String requestName);
}
