package online.itlab.springframework.validation.errors.standard.factory;

import lombok.Builder;
import online.itlab.springframework.validation.errors.standard.configuration.JvseConfig.LabelsConfig;
import online.itlab.springframework.validation.errors.standard.configuration.JvseConfig.ValuesConfig;
import online.itlab.springframework.validation.errors.standard.factory.domain.IValidationPathFactory;
import online.itlab.springframework.validation.errors.standard.factory.domain.IValidationPathFactory.ValidationPath;
import online.itlab.springframework.validation.errors.standard.factory.tools.IReflectionTools;
import online.itlab.springframework.validation.errors.standard.factory.tools.IStringTools;
import online.itlab.springframework.validation.errors.standard.factory.tools.IWebRequestTools;
import org.jspecify.annotations.Nullable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.BindParam;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public class JakartaValidationProblemDetailFactory implements IJakartaValidationProblemDetailFactory {

    private final LabelsConfig labelsConfig;
    private final ValuesConfig valuesConfig;
    private final IReflectionTools reflectionTools;
    private final IStringTools stringTools;
    private final IWebRequestTools webRequestTools;
    private final IValidationPathFactory validationPathFactory;

    public JakartaValidationProblemDetailFactory(final LabelsConfig labelsConfig,
                                                 final ValuesConfig valuesConfig,
                                                 final IReflectionTools reflectionTools,
                                                 final IStringTools stringTools,
                                                 final IWebRequestTools webRequestTools,
                                                 final IValidationPathFactory validationPathFactory) {
        this.labelsConfig = labelsConfig;
        this.valuesConfig = valuesConfig;
        this.reflectionTools = reflectionTools;
        this.stringTools = stringTools;
        this.webRequestTools = webRequestTools;
        this.validationPathFactory = validationPathFactory;
    }

    private ProblemDetail createProblemDetail() {
        final ProblemDetail problem = ProblemDetail.forStatus(valuesConfig.getStatus());

        problem.setType(valuesConfig.getType().getAbsolute());
        problem.setTitle(valuesConfig.getTitle());
        problem.setDetail(valuesConfig.getDetail());

        return problem;
    }

    @Builder
    private record ErrorDetail (
        String in,
        String name,
        String path,
        String message,
        Object rejectedValue
    ) {
        Map<String, Object> toMap(final LabelsConfig labelsConfig) {
            final Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put(labelsConfig.getIn(), in);
            errorDetails.put(labelsConfig.getName(), name);
            errorDetails.put(labelsConfig.getPath(), path);
            errorDetails.put(labelsConfig.getMessage(), message);
            errorDetails.put(labelsConfig.getRejectedValue(), rejectedValue);
            return errorDetails;
        }
    }

    /**
     * Creates an RFC 9457 error.
     * This function handles a Spring exception thrown automatically when Controller method fails validation.
     * Supports @RequestBody, @ModelAttribute.
     * @param exception Spring exception thrown in case of failed validation
     * @return
     */
    @Override
    public ProblemDetail getValidationError(final MethodArgumentNotValidException exception,
                                            final WebRequest request) {
        final ProblemDetail problem = createProblemDetail();

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

        MethodArgumentGenerators generators = getGenerators(failedMethodParameter, request);

        List<Map<String, Object>> errors = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(err -> {
                final String jsonPath = generators.path.apply(err.getField());
                final String jsonName = stringTools.lastSegment(jsonPath, '.');

                return ErrorDetail.builder()
                    .in(generators.in.apply(err.getField()))
                    .name(jsonName)
                    .path(jsonPath)
                    .message(err.getDefaultMessage())
                    .rejectedValue(err.getRejectedValue())
                    .build()
                    .toMap(labelsConfig);
            })
            .toList();

        // RFC 9457 extension members
        problem.setProperty("errors", errors);

        return problem;
    }

    private record MethodArgumentGenerators(
        Function<String, String> in,
        Function<String, String> path
    ) {}

    private MethodArgumentGenerators getGenerators(final MethodParameter failedMethodParameter,
                                                   final WebRequest request) {
        if (failedMethodParameter.hasParameterAnnotation(RequestBody.class)) {
            return new MethodArgumentGenerators(
                (failedFieldJavaPath) -> "body",
                (failedFieldJavaPath) -> reflectionTools.toJsonPath(failedMethodParameter.getParameterType(), failedFieldJavaPath)
            );
        } else if (failedMethodParameter.hasParameterAnnotation(ModelAttribute.class)) {
            return new MethodArgumentGenerators(
                (failedFieldJavaPath) -> detectSource(failedMethodParameter.getParameterType(), request, failedFieldJavaPath),
                (failedFieldJavaPath) -> getRequestParamName(failedMethodParameter.getParameterType(), failedFieldJavaPath)
            );
        } else {
            return new MethodArgumentGenerators(
                (failedFieldJavaPath) -> "parameters",
                (failedFieldJavaPath) -> failedFieldJavaPath
            );
        }
    }




    private String detectSource(final Class<?> modelAttributeType, final WebRequest request, final String validationJavaPath) {
        final String unknown = "unknown";
        if (!modelAttributeType.isRecord()) {
            return unknown;    // return generic name - only records are supported currently
        }

        // 1. javaFieldName -> requestName
        //    We find actual field and examine if it is annotated with @BindParam.
        //    If it is we used the name from @BindParam, if not javaFieldName
        // 2. Check request using requestName

//        final String requestName = getRequestParamName(modelAttributeType, fieldName);

        final ValidationPath validationPath = validationPathFactory.create(validationJavaPath);
        Field field = reflectionTools.findField(modelAttributeType, validationPath.javaFieldName());
        final BindParam bindParam = field.getAnnotation(BindParam.class);
        final String requestName =  bindParam != null
            ? bindParam.value()
            : validationPath.javaFieldName();

        var source =  webRequestTools.resolveSource(request, field, requestName);
        return source;
    }

    /**
     * validationJavaPath contains names of java fields. For
     */
    private String getRequestParamName(final Class<?> clazz, final String validationJavaPath) {
        final ValidationPath validationPath = validationPathFactory.create(validationJavaPath);
        Field field = reflectionTools.findField(clazz, validationPath.javaFieldName());
        final BindParam bindParam = field.getAnnotation(BindParam.class);
        final String requestName = bindParam != null
            ? bindParam.value()
            : validationPath.javaFieldName();
        return "%s%s".formatted(requestName, validationPath.indexPart());
    }

    @Override
    public ProblemDetail getValidationError(final HandlerMethodValidationException exception, final WebRequest request) {
        ProblemDetail pd = createProblemDetail();

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
                final var modelAttributeGenerators = new MethodArgumentGenerators(
                    (failedFieldJavaPath) -> detectSource(pe.getMethodParameter().getParameterType(), request, failedFieldJavaPath),
                    (failedFieldJavaPath) -> getRequestParamName(pe.getMethodParameter().getParameterType(), failedFieldJavaPath)
                );
                errors.addAll(fromParameterErrors(modelAttributeGenerators, pe, locale));
            }

            @Override
            public void requestBody(RequestBody ann, ParameterErrors pe) {
                final var bodyRequestGenerators = new MethodArgumentGenerators(
                    (failedFieldJavaPath) -> "body",
                    (failedFieldJavaPath) -> reflectionTools.toJsonPath(pe.getMethodParameter().getParameterType(), failedFieldJavaPath)
                );
                errors.addAll(fromParameterErrors(bodyRequestGenerators, pe, locale));
            }

            @Override
            public void requestPart(RequestPart ann, ParameterErrors pe) {
                final var bodyPartGenerators = new MethodArgumentGenerators(
                    (failedFieldJavaPath) -> "part",
                    (failedFieldJavaPath) -> failedFieldJavaPath
                );
                errors.addAll(fromParameterErrors(bodyPartGenerators, pe, locale));
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
        ProblemDetail problemDetail = createProblemDetail();

        Locale locale = LocaleContextHolder.getLocale();

        // optional custom fields
        final Map<String, Object> errorDetails = ErrorDetail.builder()
            .in("part")
            .name(exception.getRequestPartName())
            .path(exception.getRequestPartName())
            .message("Required part is not present.")
            .rejectedValue(null)
            .build()
            .toMap(labelsConfig);

        problemDetail.setProperty("errors", List.of(errorDetails));

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

        final Object rejected = r.getArgument();

        final String finalPath = path;

        return r.getResolvableErrors().stream().map(err -> {
            // for localication
            // String message = messageSource.getMessage(err, locale);
            String message = err.getDefaultMessage();

            return ErrorDetail.builder()
                .in(in)
                .name(httpName)
                .path(finalPath)
                .rejectedValue(rejected)
                .message(message)
                .build()
                .toMap(labelsConfig);
        }).toList();
    }

    private List<Map<String, Object>> fromParameterErrors(MethodArgumentGenerators generators, ParameterErrors pe, Locale locale) {
        List<Map<String, Object>> out = new ArrayList<>();

        for (FieldError fe : pe.getFieldErrors()) {
//            String message = messageSource.getMessage(fe, locale);
            String message = fe.getDefaultMessage();

            // it can be path for @RequestBody validation error
            final String javaPath = fe.getField().toString();
            final String requestPath = generators.path.apply(javaPath);
            final String name = stringTools.lastSegment(requestPath, '.');
            final String in = generators.in.apply(javaPath);

            out.add(
                ErrorDetail.builder()
                    .in(in)
                    .name(name)
                    .path(requestPath)
                    .rejectedValue(fe.getRejectedValue())
                    .message(message)
                    .build()
                    .toMap(labelsConfig)
            );
        }

        for (ObjectError oe : pe.getGlobalErrors()) {
//            String message = messageSource.getMessage(oe, locale);
            String message = oe.getDefaultMessage();

            out.add(
                ErrorDetail.builder()
                    .in("dummy")                                        // TODO cannot be like this
                    .name(pe.getObjectName())
                    .path(pe.getObjectName())
                    .rejectedValue(null)                                // TODO probably can be supplied
                    .message(message)
                    .build()
                    .toMap(labelsConfig)
            );
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
