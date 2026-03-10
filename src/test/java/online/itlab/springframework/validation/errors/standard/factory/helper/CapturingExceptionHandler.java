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

    protected WebRequest webRequest;

    protected MethodArgumentNotValidException methodArgumentNotValidException;
    protected HandlerMethodValidationException handlerMethodValidationException;
    protected MissingServletRequestPartException missingServletRequestPartException;

    public CapturingExceptionHandler() {super();}

    public void reset() {
        this.webRequest = null;
        this.methodArgumentNotValidException = null;
        this.handlerMethodValidationException = null;
        this.missingServletRequestPartException = null;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {

        System.out.println("------------------1111111111111------------------");
        this.webRequest = request;
        this.methodArgumentNotValidException = exception;
        return ResponseEntity.badRequest().build();
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
        HandlerMethodValidationException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {

        System.out.println("------------------22222222222------------------");
        this.webRequest = request;
        this.handlerMethodValidationException = exception;
        return ResponseEntity.badRequest().build();
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
        MissingServletRequestPartException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {

        System.out.println("------------------33333333333333------------------");
        this.webRequest = request;
        this.missingServletRequestPartException = exception;
        return ResponseEntity.badRequest().build();
    }
}