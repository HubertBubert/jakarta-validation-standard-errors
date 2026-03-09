package online.itlab.springframework.validation.errors.standard.factory;

import org.jspecify.annotations.Nullable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@Service
public class JakartaValidationProblemDetailFactory implements IJakartaValidationProblemDetailFactory {
    /**
     * Creates an RFC 9457 error.
     * This function handles a Spring exception thrown automatically when Controller method fails validation.
     * Supports @RequestBody, @ModelAttribute.
     * @param exception Spring exception thrown in case of failed validation
     * @return
     */
    @Override
    public ProblemDetail getValidationError(final MethodArgumentNotValidException exception) {
        final ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

        problem.setType(URI.create("/problems/validation-failed"));
        problem.setTitle("Request Validation Failed");
        problem.setDetail("Request has one or more validation errors. Please fix them and try again.");

        // - exception.getParameter()
        //   It represents the Controller method parameter which failed the validation.
        //   - getParameterType()
        //     Class of the parameter.
        //   - getParameterAnnotations()
        //     Returns annotations applied on method argument. Can be used to detect @RequestBody, @ModelAttribute

        // if @RequestBody
        // - in = 'body'
        // - path = {jsonPath}
        //   FieldError.getField() contains java path, which needs to be transformed to json path.

        // if @ModelAttribute
        // - in = {specific for failed parameter}
        //   FieldError.getField() contains java path inside model attribute class.
        //   Annotations on this field need to be inspected to determine 'in' value, as the field can be annotated
        //   with any annotation, like @PathVariable, @RequestParam, ...
        // - path = {httpName}
        //   Name specified in the annotation, or if not specified, name of field.

        final MethodParameter failedMethodParameter = exception.getParameter();

        MethodArgumentGenerators generators = getGenerators(failedMethodParameter);

        List<Map<String, String>> errors = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(err -> Map.of(
                "in", generators.in.apply(err.getField()),
                "path", generators.path.apply(err.getField()),
                "message", err.getDefaultMessage(),
//                "rejectedValue", err.getRejectedValue()
                "rejectedValue", String.valueOf(err.getRejectedValue())
            ))
            .toList();

        // RFC 9457 extension members
        problem.setProperty("errors", errors);

        return problem;
    }

    private record MethodArgumentGenerators(
        Function<String, String> in,
        Function<String, String> path
    ) {}

    private MethodArgumentGenerators getGenerators(final MethodParameter failedMethodParameter) {
        if (failedMethodParameter.hasParameterAnnotation(RequestBody.class)) {
            return new MethodArgumentGenerators(
                (failedFieldJavaPath) -> "body",
                (failedFieldJavaPath) -> failedFieldJavaPath
            );
        } else if (failedMethodParameter.hasParameterAnnotation(ModelAttribute.class)) {
            return new MethodArgumentGenerators(
                (failedFieldJavaPath) -> detectSource(failedMethodParameter.getParameterType(), failedFieldJavaPath),
                (failedFieldJavaPath) -> failedFieldJavaPath
            );
        } else {
            return new MethodArgumentGenerators(
                (failedFieldJavaPath) -> " parameters",
                (failedFieldJavaPath) -> failedFieldJavaPath
            );
        }
    }

    private String detectSource(final Class<?> clazz, final String fieldName) {
        final String unknown = "unknown";
        if (!clazz.isRecord()) {
            return unknown;    // return generic name - only records are supported currently
        }

        Class<?>[] types = Arrays.stream(clazz.getRecordComponents())
            .map(RecordComponent::getType)
            .toArray(Class<?>[]::new);

        try {
            Constructor<?> canonical = clazz.getDeclaredConstructor(types);
            Parameter[] params = canonical.getParameters();
            RecordComponent[] components = clazz.getRecordComponents();

            for (int i = 0; i < components.length; i++) {
                if (!components[i].getName().equals(fieldName)) {       // this won't work for different names annotaiton name != field name
                    continue;
                }
                System.out.println(components[i].getName());
                Parameter param = params[i];

                if (param.isAnnotationPresent(PathVariable.class)) {
                    return "path";
                } else if (param.isAnnotationPresent(RequestParam.class)) {
                    return "query";
                } else if (param.isAnnotationPresent(RequestHeader.class)) {
                    return "header";
                } else {
                    return unknown;
                }
            }
        } catch (NoSuchMethodException e) {
            return unknown;
        }
        return unknown;
    }

    @Override
    public ProblemDetail getValidationError(final HandlerMethodValidationException exception) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create("/problems/validation-failed"));
        pd.setTitle("Request Validation Failed");
        pd.setDetail("Request has one or more validation errors. Please fix them and try again.");

//        if (request instanceof ServletWebRequest swr) {
//            HttpServletRequest r = swr.getRequest();
//            pd.setInstance(URI.create(r.getRequestURI()));
//        }

        Locale locale = LocaleContextHolder.getLocale();
        List<Map<String, Object>> errors = new ArrayList<>();

        exception.visitResults(new HandlerMethodValidationException.Visitor() {

            @Override
            public void pathVariable(PathVariable ann, ParameterValidationResult r) {
                errors.addAll(fromValidationResult(
                    "path",
                    httpNameForPathVariable(ann, r),
                    r,
                    locale
                ));
            }

            @Override
            public void requestParam(@Nullable RequestParam ann, ParameterValidationResult r) {
                errors.addAll(fromValidationResult(
                    "query",
                    httpNameForRequestParam(ann, r),
                    r,
                    locale
                ));
            }

            @Override
            public void matrixVariable(MatrixVariable ann, ParameterValidationResult r) {
                errors.addAll(fromValidationResult(
                    "matrix",
                    httpNameForMatrixVariable(ann, r),
                    r,
                    locale
                ));
            }

            @Override
            public void requestHeader(RequestHeader ann, ParameterValidationResult r) {
                errors.addAll(fromValidationResult(
                    "header",
                    httpNameForRequestHeader(ann, r),
                    r,
                    locale
                ));
            }

            @Override
            public void cookieValue(CookieValue ann, ParameterValidationResult r) {
                errors.addAll(fromValidationResult(
                    "cookie",
                    httpNameForCookieValue(ann, r),
                    r,
                    locale
                ));
            }

            @Override
            public void modelAttribute(@Nullable ModelAttribute ann, ParameterErrors pe) {
                errors.addAll(fromParameterErrors("query", pe, locale));
            }

            @Override
            public void requestBody(RequestBody ann, ParameterErrors pe) {
                errors.addAll(fromParameterErrors("body", pe, locale));
            }

            @Override
            public void requestPart(RequestPart ann, ParameterErrors pe) {
                errors.addAll(fromParameterErrors("part", pe, locale));
            }

            @Override
            public void other(ParameterValidationResult r) {
                errors.addAll(fromValidationResult(
                    "parameter",
                    javaParamNameFallback(r),
                    r,
                    locale
                ));
            }
        });

        // RFC 9457 extension members
        pd.setProperty("errors", errors);
        return pd;
    }

    // Missing @RequestPart
    @Override
    public ProblemDetail getValidationError(final MissingServletRequestPartException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setType(URI.create("/problems/validation-failed"));
        problemDetail.setTitle("Request Validation Failed");
        problemDetail.setDetail("Request has one or more validation errors. Please fix them and try again.");

        Locale locale = LocaleContextHolder.getLocale();

        // optional custom fields
        final List<Map<String, Object>> errors = List.of(
            Map.of(
                "in", "part",
                "name", exception.getRequestPartName(),
                "message", "Required part is not present.",
                "rejectedValue", "(no value)"
            )
        );

        problemDetail.setProperty("errors", errors);

        return problemDetail;
    }

    private List<Map<String, Object>> fromValidationResult(
        String in,
        String httpName,
        ParameterValidationResult r,
        Locale locale) {

        // container element constraints (List<@NotNull ...>, etc.)
        String path = httpName;
        if (r.getContainerIndex() != null) {
            path += "[" + r.getContainerIndex() + "]";
        } else if (r.getContainerKey() != null) {
            path += "[" + r.getContainerKey() + "]";
        }

        Object rejected = r.getArgument();

        final String finalPath = path;

        return r.getResolvableErrors().stream().map(err -> {
            // for localication
            // String message = messageSource.getMessage(err, locale);
            String message = err.getDefaultMessage();

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("in", in);                 // path/query/header/...
            m.put("name", httpName);         // HTTP-level name: id, firstName, ...
            m.put("path", finalPath);             // includes [index]/[key] if applicable
            m.put("rejectedValue", rejected);
            m.put("message", message);
            return m;
        }).toList();
    }

    private List<Map<String, Object>> fromParameterErrors(String in, ParameterErrors pe, Locale locale) {
        List<Map<String, Object>> out = new ArrayList<>();

        for (FieldError fe : pe.getFieldErrors()) {
//            String message = messageSource.getMessage(fe, locale);
            String message = fe.getDefaultMessage();

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("in", in);
            m.put("name", fe.getField());            // for body/model attributes, field path is the public name
            m.put("path", fe.getField());
            m.put("rejectedValue", fe.getRejectedValue());
            m.put("message", message);
            out.add(m);
        }

        for (ObjectError oe : pe.getGlobalErrors()) {
//            String message = messageSource.getMessage(oe, locale);
            String message = oe.getDefaultMessage();

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("in", in);
            m.put("name", pe.getObjectName());
            m.put("path", pe.getObjectName());
            m.put("message", message);
            out.add(m);
        }

        return out;
    }

    // ---------- Name resolution (HTTP name first) ----------

    private String httpNameForPathVariable(PathVariable ann, ParameterValidationResult r) {
        String name = nullIfBlank(ann.value());
        return name != null ? name : javaParamNameFallback(r);

    }

    private String httpNameForRequestParam(@Nullable RequestParam ann, ParameterValidationResult r) {
        String name = nullIfBlank(ann.value());
        return name != null ? name : javaParamNameFallback(r);
    }

    private String httpNameForMatrixVariable(MatrixVariable ann, ParameterValidationResult r) {
        String name = nullIfBlank(ann.value());
        return name != null ? name : javaParamNameFallback(r);
    }

    private String httpNameForRequestHeader(RequestHeader ann, ParameterValidationResult r) {
        String name = nullIfBlank(ann.value());
        return name != null ? name : javaParamNameFallback(r);
    }

    private String httpNameForCookieValue(CookieValue ann, ParameterValidationResult r) {
        String name = nullIfBlank(ann.value());
        return name != null ? name : javaParamNameFallback(r);
    }

    private String javaParamNameFallback(ParameterValidationResult r) {
        String n = r.getMethodParameter().getParameterName();
        if (n == null || n.isBlank()) {
            n = "arg" + r.getMethodParameter().getParameterIndex();
        }
        return n;
    }

    private static @Nullable String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

}
