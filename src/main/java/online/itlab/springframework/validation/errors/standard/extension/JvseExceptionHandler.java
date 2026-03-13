package online.itlab.springframework.validation.errors.standard.extension;

import online.itlab.springframework.validation.errors.standard.configuration.JvseConfig;
import online.itlab.springframework.validation.errors.standard.factory.IJakartaValidationProblemDetailFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    private JvseConfig jvseConfiguration;

    @Autowired
    public final void setLogRepository(final IJakartaValidationProblemDetailFactory problemFactory) {
        this.problemFactory = problemFactory;
    }

    @Autowired
    public final void JvseConfiguration(final JvseConfig jvseConfiguration) {
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
            return handleExceptionInternal(exception, problem, headers, HttpStatus.valueOf(problem.getStatus()), request);
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
            return handleExceptionInternal(exception, problem, headers, HttpStatus.valueOf(problem.getStatus()), request);
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
            return handleExceptionInternal(exception, problem, headers, HttpStatus.valueOf(problem.getStatus()), request);
        } else {
            return super.handleMissingServletRequestPart(exception, headers, status, request);
        }
    }
}
