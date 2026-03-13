package online.itlab.springframework.validation.errors.standard.extension;

import online.itlab.springframework.validation.errors.standard.configuration.JvseConfiguration;
import online.itlab.springframework.validation.errors.standard.factory.IJakartaValidationProblemDetailFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

public abstract class JvseExceptionHandler extends ResponseEntityExceptionHandler {

    private IJakartaValidationProblemDetailFactory problemFactory;
    private JvseConfiguration jvseConfiguration;

    @Autowired
    public final void setLogRepository(final IJakartaValidationProblemDetailFactory problemFactory) {
        this.problemFactory = problemFactory;
    }

    @Autowired
    public final void JvseConfiguration(final JvseConfiguration jvseConfiguration) {
        this.jvseConfiguration = jvseConfiguration;
    }

    public JvseExceptionHandler() {
        super();
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        final MethodArgumentNotValidException exception,
        final HttpHeaders headers,
        final HttpStatusCode status,
        final WebRequest request) {

        if (jvseConfiguration.isEnabled()) {
            final ProblemDetail problem = problemFactory.getValidationError(exception, request);
            return handleExceptionInternal(exception, problem, headers, status, request);
        } else {
            return super.handleMethodArgumentNotValid(exception, headers, status, request);
        }
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
        final HandlerMethodValidationException exception,
        final HttpHeaders headers,
        final HttpStatusCode status,
        final WebRequest request) {

        if (jvseConfiguration.isEnabled()) {
            final ProblemDetail problem = problemFactory.getValidationError(exception, request);
            return new ResponseEntity<>(problem, headers, status);
        } else {
            return super.handleHandlerMethodValidationException(exception, headers, status, request);
        }
    }

    // TODO tbd if reaction to missing params will stay
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
        MissingServletRequestPartException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {

        if (jvseConfiguration.isEnabled()) {
            final ProblemDetail problem = problemFactory.getValidationError(exception);
            return new ResponseEntity<>(problem, headers, status);
        } else {
            return super.handleMissingServletRequestPart(exception, headers, status, request);
        }
    }
}
