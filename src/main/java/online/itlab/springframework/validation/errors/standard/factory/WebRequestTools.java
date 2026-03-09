package online.itlab.springframework.validation.errors.standard.factory;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class WebRequestTools implements IWebRequestTools {

    public String resolveSource(final WebRequest webRequest,
                                       final Field field,
                                       final String requestName
    ) {
        Objects.requireNonNull(webRequest, "req must not be null");
        Objects.requireNonNull(requestName, "requestName must not be null");

        final ServletWebRequest servletWebRequest = (ServletWebRequest) webRequest;

        // 1) MultipartFile fields bind from multipart parts.
        // We check this first because a file part is not a normal request parameter.
        if (MultipartFile.class.isAssignableFrom(field.getType())) {
            MultipartHttpServletRequest multipartRequest =
                servletWebRequest.getNativeRequest(MultipartHttpServletRequest.class);

            if (multipartRequest != null && multipartRequest.getFile(requestName) != null) {
                return "multi";
            }
        }

        // 2) Request parameters win over path variables and headers.
        // In Servlet API this includes query params and form fields.
        if (servletWebRequest.getParameter(requestName) != null) {
            return "query";
        }

        final HttpServletRequest request = asHttpServletRequest(servletWebRequest);

        // 3) Path variables are exposed as a request attribute map.
        Map<String, String> pathVariables = getPathVariables(request);
        if (pathVariables.containsKey(requestName)) {
            return "path";
        }

        // 4) Headers are considered last, and Spring strips dashes from header names
        // when binding onto @ModelAttribute properties.
        if (hasBindableHeader(request, requestName)) {
            return "header";
        }

        return null;
    }

    private HttpServletRequest asHttpServletRequest(WebRequest webRequest) {
        HttpServletRequest request = ((NativeWebRequest) webRequest)
            .getNativeRequest(HttpServletRequest.class);

        if (request == null) {
            throw new IllegalArgumentException("WebRequest is not backed by HttpServletRequest");
        }
        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getPathVariables(HttpServletRequest request) {
        Object value = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, String>) map;
        }
        return Collections.emptyMap();
    }

    private boolean hasBindableHeader(HttpServletRequest request, String fieldName) {
        String normalizedFieldName = normalizeHeaderNameForModelBinding(fieldName);

        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return false;
        }

        while (headerNames.hasMoreElements()) {
            String rawHeaderName = headerNames.nextElement();
            String normalizedHeaderName = normalizeHeaderNameForModelBinding(rawHeaderName);

            if (normalizedHeaderName.equals(normalizedFieldName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Spring model binding strips dashes from header names.
     * Example:
     *   "X-Request-Id" -> "xrequestid"
     *   "xRequestId"   -> "xrequestid"
     */
    private String normalizeHeaderNameForModelBinding(String name) {
        return name.replace("-", "").toLowerCase(Locale.ROOT);
    }
}
