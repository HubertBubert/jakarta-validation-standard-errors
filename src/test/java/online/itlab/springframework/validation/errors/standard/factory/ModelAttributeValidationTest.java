package online.itlab.springframework.validation.errors.standard.factory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class ModelAttributeValidationTest {

    RestTestClient client;
    CapturingExceptionHandler controllerAdvice;

    IJakartaValidationProblemDetailFactory testedProblemFactory;

    @BeforeEach
    public void setup() {
        // client configuration
        controllerAdvice = new CapturingExceptionHandler();
        client = RestTestClient
            .bindToController(new TestModelAttributeController())
            .configureServer(mockMvc -> {
                mockMvc.setControllerAdvice(controllerAdvice);
            }).build();

        // logic configuration
        LibAutoConfiguration autoConfiguration = new LibAutoConfiguration();
        testedProblemFactory = autoConfiguration.problemDetailFactory();
    }

//    Expected :{errors=[{path=id, rejectedValue=i, name=id, in=matrix, message=size must be between 2 and 5}]}
//    Actual   :{errors=[{message=size must be between 2 and 5, path=id, in=body, rejectedValue=i}]}
    // problems
    // 1. in=body -> but should be path, query, header
    // 2. errors should be the same like in path, query and header specific tests

    @ParameterizedTest
    @MethodSource("singleCasesProvider")
    public void singleModelAttributeIncorrect(final String baseUrl,
                                              final String idParameter,
                                              final String nameParameter,
                                              final String headerParameter,
                                              final String expectedIn,
                                              final String expectedName,
                                              final Object expectedRejectedValue,
                                              final String expectedMessage) {

        var url = baseUrl + "/%s?name=%s".formatted(idParameter, nameParameter);
        var result = client.get()
            .uri(url)
            .header("header", headerParameter)
            .exchange().returnResult();

        var exception = controllerAdvice.getMethodArgumentNotValidException();
        assertNotNull(exception);

        // when:
        var problemDetail = testedProblemFactory.getValidationError(exception);

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
                        "in", expectedIn,
//                        "name", expectedName,         // this must be compatible with specific handlers for @PathVariable, @RequestParam
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
                "i",
                "name",
                "header",
                "path",
                "id",
                "i",
                "size must be between 2 and 5"
            ),
            arguments(
                "/test/same",
                "id",
                "    ",
                "header",
                "query",
                "name",
                "    ",
                "must not be blank"
            ),
            arguments(
                "/test/same",
                "id",
                "name",
                "longHeader",
                "header",
                "header",
                "longHeader",
                "size must be between 1 and 6"
            ),
            arguments(
                "/test/noname",
                "i",
                "name",
                "header",
                "path",
                "id",
                "i",
                "size must be between 2 and 5"
            ),
            arguments(
                "/test/noname",
                "id",
                "    ",
                "header",
                "query",
                "name",
                "    ",
                "must not be blank"
            ),
            arguments(
                "/test/noname",
                "id",
                "name",
                "longHeader",
                "header",
                "header",
                "longHeader",
                "size must be between 1 and 6"
            )
            // ModelAttribute does not support renamed parameters
//,
//            arguments(
//                "/test/renamed",
//                "i",
//                "name",
//                "header",
//                "path",
//                "id",
//                "i",
//                "size must be between 2 and 5"
//            ),
//            arguments(
//                "/test/renamed",
//                "id",
//                "    ",
//                "header",
//                "query",
//                "name",
//                "    ",
//                "must not be blank"
//            ),
//            arguments(
//                "/test/renamed",
//                "id",
//                "name",
//                "longHeader",
//                "header",
//                "header",
//                "longHeader",
//                "size must be between 1 and 6"
//            )
        );
    }

    @ParameterizedTest
    @MethodSource("multipleCasesProvider")
    public void multipleModelAttributeIncorrect(final String baseUrl,
                                                final String incorrectIdParameter,
                                                final String incorrectNameParameter,
                                                final String incorrectHeaderParameter,
                                                final String expectedIdMessage,
                                                final String expectedNameMessage,
                                                final Object expectedHeaderMessage) {

        var url = baseUrl + "/%s?name=%s".formatted(incorrectIdParameter, incorrectNameParameter);
        client.get()
            .uri(url)
            .header("header", incorrectHeaderParameter)
            .exchange();

        var exception = controllerAdvice.getMethodArgumentNotValidException();
        assertNotNull(exception);

        // when:
        var problemDetail = testedProblemFactory.getValidationError(exception);

        // then:
        assertEquals(URI.create("/problems/validation-failed"), problemDetail.getType());
        assertEquals("Request Validation Failed", problemDetail.getTitle());
        assertEquals(HttpStatus.BAD_REQUEST.value(), problemDetail.getStatus());
        assertEquals("Request has one or more validation errors. Please fix them and try again.", problemDetail.getDetail());

        final List<Map<String, Object>> expectedErrorsList = List.of(
            Map.of(
                "in", "path",
                "path", "id",
                "rejectedValue", incorrectIdParameter,
                "message", expectedIdMessage
            ),
            Map.of(
                "in", "query",
                "path", "name",
                "rejectedValue", incorrectNameParameter,
                "message", expectedNameMessage
            ),
            Map.of(
                "in", "header",
                "path", "header",
                "rejectedValue", incorrectHeaderParameter,
                "message", expectedHeaderMessage
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
                "i",
                "    ",
                "longHeader",
                "size must be between 2 and 5",
                "must not be blank",
                "size must be between 1 and 6"
            ),
            arguments(
                "/test/noname",
                "i",
                "    ",
                "longHeader",
                "size must be between 2 and 5",
                "must not be blank",
                "size must be between 1 and 6"
            )//,
            // ModelAttribute does not support renamed parameters
//            arguments(
//                "/test/noname",
//                "i",
//                "    ",
//                "longHeader",
//                "size must be between 2 and 5",
//                "must not be blank",
//                "size must be between 1 and 6"
//            )
        );
    }

    @RestController
    @RequestMapping("/test")
    class TestModelAttributeController {

        @GetMapping("/same/{id}")
        String getSameName(final @ModelAttribute @Valid Same same) {
            return "%s-%s-%s".formatted(same.id, same.name, same.header);
        }

        @GetMapping("/noname/{id}")
        String getNoName(final @ModelAttribute @Valid Noname noname) {
            return "%s-%s-%s".formatted(noname.id, noname.name, noname.header);
        }

        @GetMapping("/renamed/{id}")
        String getNoName(final @ModelAttribute @Valid Renamed renamed) {
            return "%s-%s-%s".formatted(renamed.identifier, renamed.fullName, renamed.headerValue);
        }
    }

    record Same(
        @PathVariable("id")
        @Size(min = 2, max = 5)
        String id,

        @RequestParam("name")
        @NotBlank @Size(min = 1, max = 100)
        String name,

        @RequestHeader("header")
        @NotBlank @Size(min = 1, max = 6)
        String header
    ){}

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

    record Noname(
        @PathVariable
        @Size(min = 2, max = 5)
        String id,

        @RequestParam
        @NotBlank @Size(min = 1, max = 100)
        String name,

        @RequestHeader
        @NotBlank @Size(min = 1, max = 6)
        String header
    ){}
}
