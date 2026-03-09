package online.itlab.springframework.validation.errors.standard.factory.helper;

import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Getter
@RestControllerAdvice
public class CapturingExceptionHandler extends ResponseEntityExceptionHandler {

    protected MethodArgumentNotValidException methodArgumentNotValidException;
    protected HandlerMethodValidationException handlerMethodValidationException;
    protected MissingServletRequestPartException missingServletRequestPartException;

    public CapturingExceptionHandler() {super();}

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {
        this.methodArgumentNotValidException = exception;
        return ResponseEntity.noContent().build();
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
        HandlerMethodValidationException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {
        this.handlerMethodValidationException = exception;
        return ResponseEntity.noContent().build();
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
        MissingServletRequestPartException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {
        this.missingServletRequestPartException = exception;
        return ResponseEntity.badRequest().build();
    }
}