# What is this

**Jakarta Validation Standard Errors** is a library for **Spring Boot**, which produces detailed RFC 9457 
errors for REST API validated using **Jakarta Validation**.

## What is RFC 9457
**RFC 9457 - Problem Details for HTTP APIs** is a standard that describes HTTP error response format.
No need to re-invent the wheel.  

## Why to use this library

**Spring Boot** has support for RFC 9457. It can automatically return validation errors in RFC 9457 format.
All we need to do is to create a `@ControllerAdvice` which extends `ResponseEntityExceptionHandler`:
```java
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Getter
@RestControllerAdvice
public class OurControllerAdvice extends ResponseEntityExceptionHandler {
    // no-op
}
```
The problem is that these errors are:
- cryptic
- non-descriptive
- non-actionable  

Standard Spring error example:
```json
{
  "status": 400,
  "title": "Bad Request",
  "instance": "/temp/same/employees/99",
  "detail": "Validation failure"
}
```

Like we can see, `detail` field contains no details at all.  
It does not help users at all to fix the incorrect request.

Here is the message produces by the library for the same request:
```json
{
  "type": "/problems/validation-failed",
  "status": 400,
  "title": "Request Validation Failed",
  "instance": "/temp/same/employees/99",
  "detail": "Request has one or more validation errors. Please fix them and try again.",
  "errors": [
    {
      "in": "path",
      "name": "id",
      "path": "id",
      "rejectedValue": 99,
      "message": "must be greater than or equal to 100"
    },
    {
      "in": "header",
      "name": "token",
      "path": "token",
      "rejectedValue": "badToken",
      "message": "size must be between 16 and 16"
    },
    {
      "in": "body",
      "name": "height",
      "path": "person.height",
      "rejectedValue": 0,
      "message": "must be greater than 0"
    },
    {
      "in": "body",
      "name": "firstName",
      "path": "person.firstName",
      "rejectedValue": "A",
      "message": "size must be between 2 and 100"
    }
  ]
}
```

## Library features

- provides full list of rejected values with `location` 
- error details use HTTP request names, not java class field names reported by Jakarta Validation  
  Supported name annotations are:
  - `@JsonProperty` for `@RequestBody`
  - `@BindParam` for `@ModelAttribute`
- hides **Spring** validation flow ambiguity  
  Based on the Controller method signature, validation errors for `@RequestBody` and `@ModelAttribute`
  are reported in two different ways. This library guarantees the same error format regardless of the flow.

  