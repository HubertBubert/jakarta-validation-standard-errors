package online.itlab.springframework.validation.errors.standard.extension;

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

public abstract class StandardErrorsExceptionHandler extends ResponseEntityExceptionHandler {

    private IJakartaValidationProblemDetailFactory problemFactory;

    @Autowired
    public final void setLogRepository(final IJakartaValidationProblemDetailFactory problemFactory) {
        this.problemFactory = problemFactory;
    }

    public StandardErrorsExceptionHandler() {
        super();
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        final MethodArgumentNotValidException exception,
        final HttpHeaders headers,
        final HttpStatusCode status,
        final WebRequest request) {

        final ProblemDetail problem = problemFactory.getValidationError(exception, request);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
        final HandlerMethodValidationException exception,
        final HttpHeaders headers,
        final HttpStatusCode status,
        final WebRequest request) {

        final ProblemDetail problem = problemFactory.getValidationError(exception, request);
        return new ResponseEntity<>(problem, headers, status);
    }

    // TODO tbd if reaction to missing params will stay
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
        MissingServletRequestPartException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {

        final ProblemDetail problem = problemFactory.getValidationError(exception);
        return new ResponseEntity<>(problem, headers, status);
    }
}
