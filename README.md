# What is this

**Jakarta Validation Standard Errors** is a library for **Spring Boot**, which produces detailed RFC 9457 
errors for REST API validated using **Jakarta Validation**.

## What is RFC 9457
**RFC 9457 - Problem Details for HTTP APIs** is a standard that describes HTTP error response format.
No need to re-invent the wheel.  

## Why to use this library

**Spring Boot** has support for RFC 9457. It can automatically return validation errors in RFC 9457 format.  

Standard Spring error example:
```json
{
  "status": 400,
  "title": "Bad Request",
  "instance": "/temp/same/employees/99",
  "detail": "Validation failure"
}
```

The problem is that these errors are not helpful as they are:
- cryptic
- non-descriptive
- non-actionable

Here is the message produced by the library for the same request:
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
      "path": "friends[0].firstName",
      "rejectedValue": "B",
      "message": "size must be between 2 and 100"
    }
  ]
}
```

Everything is now clear.

## Library features

- provides full list of rejected values with `location` 
- error details use HTTP request names, not java class field names reported by Jakarta Validation  
  Supported name annotations are:
  - `@JsonProperty` for `@RequestBody`
  - `@BindParam` for `@ModelAttribute`
- hides **Spring** validation flow ambiguity  
  Based on the Controller method signature, validation errors for `@RequestBody` and `@ModelAttribute`
  are reported in two different ways. This library guarantees the same error format regardless of the flow.

## How to use this library

Just create a `@ConstrollerAdvice` and extend `JvseExceptionHandler` instead of `ResponseEntityExceptionHandler`
provided by Spring.

```java
import online.itlab.springframework.validation.errors.standard.extension.JvseExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class JakartaStandardExceptionHandler extends JvseExceptionHandler {}
```

That's it!  
Now you can configure the behavior to your liking.

## Configuration

All configuration options for the library are stored under the root key: `jvse`.

Configuration options:

| name                     | type       | default value                                                               | description                                                                                     |
|--------------------------|------------|-----------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `jvse.enabled`           | boolean    | `true`                                                                      | Enables or disables the library. <br>When disabled it produces standard Spring RFC 9457 errors. |
| `jvse.values.type.base`  | URI        | null                                                                        | Used to build value for the `error.type` field.                                                 |
| `jvse.values.type.value` | URI        | `/problems/validation-failed`                                               | Used to build value for the `error.type` field.                                                 |
| `jvse.values.title`      | String     | `Request Validation Failed`                                                 | Value for the `error.title` field.                                                              |
| `jvse.values.detail`     | String     | `Request has one or more validation errors. Please fix them and try again.` | Value for the `error.detail` field.                                                             |
| `jvse.values.status`     | HttpStatus | `BAD_REQUEST`                                                               | Value for the `error.status` field.                                                             |

### error.type

According to RFC 9457 the `type` value should be an absolute resolvable URI when possible.
The library by default sets relative URI, as it is unable to guess od detect the actual domain.
The actual production setup may significantly differ between deployments.

If the domain is static and known before the deployment the `jvse.values.type.base` can be set.  
This guarantees the absolute URI in the `error.type` field.

