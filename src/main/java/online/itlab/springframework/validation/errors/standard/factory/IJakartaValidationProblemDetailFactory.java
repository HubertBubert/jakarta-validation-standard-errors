package online.itlab.springframework.validation.errors.standard.factory;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

public interface IJakartaValidationProblemDetailFactory {
    ProblemDetail getValidationError(MethodArgumentNotValidException exception, WebRequest request);
    ProblemDetail getValidationError(HandlerMethodValidationException exception, WebRequest request);
    ProblemDetail getValidationError(MissingServletRequestPartException exception);
}
