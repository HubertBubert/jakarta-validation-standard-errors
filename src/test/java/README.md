## Known issues

- `@ModelAttribute` does not support different names
  ```java
  @GetMapping("/renamed/{id}")
  String getNoName(final @ModelAttribute @Valid Renamed renamed) {
      return "%s-%s-%s".formatted(renamed.identifier, renamed.fullName, renamed.headerValue);
  }  
  ```
  ```java
  record Renamed(
      @PathVariable("id")
      @Size(min = 2, max = 5)
      String identifier,
  
      @RequestParam("name")
      @NotBlank @Size(min = 1, max = 100)
      String fullName,
  
      @RequestHeader("header")
      @NotBlank @Size(min = 1, max = 6)
      String headerValue
  ){}
  ```
  Problem: all values are null.
- `RestTestClient` does not set multiple cookies right
  ```java
  client.get()
    .uri(url)
    .cookie("name", nameCookieValue)
    .cookie("year", Integer.toString(yearCookieValue))
    .exchange();
  ```
  Causes this error:
  ```
  Required cookie 'year' is not present.
  ```
  If we switch the order of setting the cookies:
  ```java
  client.get()
    .uri(url)
    .cookie("year", Integer.toString(yearCookieValue))
    .cookie("name", nameCookieValue)
    .exchange();
  ```
  ```
  Required cookie 'name' is not present.
  ```
- `HandlerMethodValidationException.visitResults()`  
  Never reaches 
  ```java
  @Override
  public void requestPart(RequestPart ann, ParameterErrors pe) { }
  ```
  An exception is thrown from Spring internals.

  