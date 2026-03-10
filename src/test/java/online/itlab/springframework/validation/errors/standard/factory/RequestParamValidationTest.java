package online.itlab.springframework.validation.errors.standard.factory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import online.itlab.springframework.validation.errors.standard.autoconfigure.LibAutoConfiguration;
import online.itlab.springframework.validation.errors.standard.factory.helper.CapturingExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class RequestParamValidationTest {

    RestTestClient client;
    CapturingExceptionHandler controllerAdvice;

    IJakartaValidationProblemDetailFactory testedProblemFactory;

    @BeforeEach
    public void setup() {
        // client configuration
        controllerAdvice = new CapturingExceptionHandler();
        client = RestTestClient
            .bindToController(new TestRequestParamController())
            .configureServer(mockMvc -> {
                mockMvc.setControllerAdvice(controllerAdvice);
            }).build();

        // logic configuration
        LibAutoConfiguration autoConfiguration = new LibAutoConfiguration();
        testedProblemFactory = autoConfiguration.problemDetailFactory();
    }

    @ParameterizedTest
    @MethodSource("singleCasesProvider")
    public void singleQueryParamIncorrect(final String baseUrl,
                                          final String nameParameter,
                                          final int pageParameter,
                                          final String expectedName,
                                          final Object expectedRejectedValue,
                                          final String expectedMessage) {

        var url = baseUrl + "?name=%s&page=%d".formatted(nameParameter, pageParameter);
        client.get()
            .uri(url)
            .exchange()
            .returnResult();

        var exception = controllerAdvice.getHandlerMethodValidationException();
        var webRequest = controllerAdvice.getWebRequest();
        assertNotNull(exception);
        assertNotNull(webRequest);

        // when:
        var problemDetail = testedProblemFactory.getValidationError(exception, webRequest);

        // then:
        assertEquals(URI.create("/problems/validation-failed"), problemDetail.getType());
        assertEquals("Request Validation Failed", problemDetail.getTitle());
        assertEquals(HttpStatus.BAD_REQUEST.value(), problemDetail.getStatus());
        assertEquals("Request has one or more validation errors. Please fix them and try again.", problemDetail.getDetail());

        Map<String, List<Map<String, Object>>> expected =
            Map.of(
                "errors",
                List.of(
                    Map.of(
                        "in", "query",
                        "name", expectedName,
                        "path", expectedName,
                        "rejectedValue", expectedRejectedValue,
                        "message", expectedMessage
                    )
                )
            );

        assertEquals(expected, problemDetail.getProperties());
    }

    static Stream<Arguments> singleCasesProvider() {
        return Stream.of(
            arguments(
                "/test/same",
                "Alice",
                -1,
                "page",
                -1,
                "must be greater than 0"
            ),
            arguments(
                "/test/same",
                "     ",
                5,
                "name",
                "     ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed",
                "Alice",
                -1,
                "page",
                -1,
                "must be greater than 0"
            ),
            arguments(
                "/test/renamed",
                "     ",
                5,
                "name",
                "     ",
                "must not be blank"
            ),
            arguments(
                "/test/noname",
                "Alice",
                -1,
                "page",
                -1,
                "must be greater than 0"
            ),
            arguments(
                "/test/noname",
                "     ",
                5,
                "name",
                "     ",
                "must not be blank"
            )
        );
    }

    @ParameterizedTest
    @MethodSource("multipleCasesProvider")
    public void multipleQueryParamIncorrect(final String baseUrl,
                                            final String incorrectNameParameter,
                                            final int incorrectPageParameter,
                                            final String expectedNameParameterMessage,
                                            final Object expectedPageParameterMessage) {

        var url = baseUrl + "?name=%s&page=%d".formatted(incorrectNameParameter, incorrectPageParameter);
        client.get()
            .uri(url)
            .exchange()
            .returnResult();

        var exception = controllerAdvice.getHandlerMethodValidationException();
        var webRequest = controllerAdvice.getWebRequest();
        assertNotNull(exception);
        assertNotNull(webRequest);

        // when:
        var problemDetail = testedProblemFactory.getValidationError(exception, webRequest);

        // then:
        assertThat(problemDetail.getType()).isEqualTo(URI.create("/problems/validation-failed"));
        assertThat(problemDetail.getTitle()).isEqualTo("Request Validation Failed");
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getDetail()).isEqualTo("Request has one or more validation errors. Please fix them and try again.");

        final List<Map<String, Object>> expectedErrorsList = List.of(
            Map.of(
                "in", "query",
                "name", "name",
                "path", "name",
                "rejectedValue", incorrectNameParameter,
                "message", expectedNameParameterMessage
            ),
            Map.of(
                "in", "query",
                "name", "page",
                "path", "page",
                "rejectedValue", incorrectPageParameter,
                "message", expectedPageParameterMessage
            )
        );

        assertThat(problemDetail.getProperties()).hasSize(1);
        assertThat(problemDetail.getProperties()).containsOnlyKeys("errors");
        var actualErrorsList = (List<Map<String, Object>>) problemDetail.getProperties().get("errors");
        assertThat(actualErrorsList).containsExactlyInAnyOrderElementsOf(expectedErrorsList);
    }

    static Stream<Arguments> multipleCasesProvider() {
        return Stream.of(
            arguments(
                "/test/same",
                "     ",
                -1,
                "must not be blank",
                "must be greater than 0"
            ),
            arguments(
                "/test/renamed",
                "     ",
                -1,
                "must not be blank",
                "must be greater than 0"
            ),
            arguments(
                "/test/noname",
                "     ",
                -1,
                "must not be blank",
                "must be greater than 0"
            )
        );
    }

    @ParameterizedTest
    @MethodSource("multiValueParamProvider")
    public void multiValueParamIncorrect(final List<String> nameParameters,
                                         final String expectedPath,
                                         final Object expectedRejectedValue,
                                         final String expectedMessage) {

        var queryParams = nameParameters.stream()
            .map(v -> "name=" + v)
            .collect(Collectors.joining("&", "?", ""));

        var url = "/test/multiple" + queryParams;
        client.get()
            .uri(url)
            .exchange();

        var exception = controllerAdvice.getHandlerMethodValidationException();
        var webRequest = controllerAdvice.getWebRequest();
        assertNotNull(exception);
        assertNotNull(webRequest);

        // when:
        var problemDetail = testedProblemFactory.getValidationError(exception, webRequest);

        // then:
        assertEquals(URI.create("/problems/validation-failed"), problemDetail.getType());
        assertEquals("Request Validation Failed", problemDetail.getTitle());
        assertEquals(HttpStatus.BAD_REQUEST.value(), problemDetail.getStatus());
        assertEquals("Request has one or more validation errors. Please fix them and try again.", problemDetail.getDetail());

        final Map<String, Object> errorDetailsMap = new HashMap<>();
        errorDetailsMap.put("in", "query");
        errorDetailsMap.put("name", "name");
        errorDetailsMap.put("path", expectedPath);
        errorDetailsMap.put("rejectedValue", expectedRejectedValue);
        errorDetailsMap.put("message", expectedMessage);

        Map<String, List<Map<String, Object>>> expected =
            Map.of(
                "errors",
                List.of(
                    errorDetailsMap
                )
            );

        assertEquals(expected, problemDetail.getProperties());
    }

    static Stream<Arguments> multiValueParamProvider() {
        return Stream.of(
            arguments(
                List.of("o", "two", "three"),
                "name[0]",
                "o",
                "size must be between 2 and 16"
            ),
            arguments(
                List.of("one", "t", "three"),
                "name[1]",
                "t",
                "size must be between 2 and 16"
            ),
            arguments(
                List.of("one", "two", "t"),
                "name[2]",
                "t",
                "size must be between 2 and 16"
            ),
            arguments(
                Collections.emptyList(),
                "name",
                null,
                "must not be empty"
            )
        );
    }


    @RestController
    @RequestMapping("/test")
    class TestRequestParamController {

        @GetMapping("/same")
        String getSameName(final @RequestParam("name") @NotBlank @Size(min = 1, max = 100) String name,
                           final @RequestParam("page") @Positive int page) {
            return "%s-%s".formatted(name, page);
        }

        @GetMapping("/renamed")
        String getRenamed(final @RequestParam("name") @NotBlank @Size(min = 1, max = 100) String nameFilter,
                          final @RequestParam("page") @Positive int pageNumber) {
            return "%s-%s".formatted(nameFilter, pageNumber);
        }

        @GetMapping("/noname")
        String getNoName(final @RequestParam @NotBlank @Size(min = 1, max = 100) String name,
                         final @RequestParam @Positive int page) {
            return "%s-%s".formatted(name, page);
        }

        @GetMapping("/multiple")
        String getMultipleRequestParams(final @RequestParam(required = false) @NotEmpty List<@Size(min=2, max=16) String> name) {
            return "ok";
        }
    }
}
