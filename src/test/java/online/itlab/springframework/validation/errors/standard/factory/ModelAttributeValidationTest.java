package online.itlab.springframework.validation.errors.standard.factory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import online.itlab.springframework.validation.errors.standard.autoconfigure.LibAutoConfiguration;
import online.itlab.springframework.validation.errors.standard.factory.helper.CapturingExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.BindParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

// NOTE:
// Even though @ModelAttribute supports @RequestPart, Jakarta Validations do not have validations for this
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

    public static void main(String ...args) {
        var nameParameters = List.of("Alice", "Sharon");
        var queryParams = nameParameters.stream()
            .map(v -> "name=" + v)
            .collect(Collectors.joining("&", "?", ""));
        System.out.println(queryParams);
    }

    @ParameterizedTest
    @MethodSource("singleCasesProvider")
    public void singleModelAttributeIncorrect(final String baseUrl,
                                              final String idParameter,
                                              final List<String> nameParameters,
                                              final String headerParameter,
                                              final String expectedIn,
                                              final String expectedName,
                                              final Object expectedRejectedValue,
                                              final String expectedMessage) {
        var queryParams = nameParameters.stream()
            .map(v -> "name=" + v)
            .collect(Collectors.joining("&", "?", ""));
        var url = baseUrl + "/%s%s".formatted(idParameter, queryParams);

        var result = client.get()
            .uri(url)
            .header("header", headerParameter)
            .exchange()
            .expectBody(ProblemDetail.class)
            .returnResult();

        var exception = controllerAdvice.getMethodArgumentNotValidException();
        var webRequest = controllerAdvice.getWebRequest();
        assertThat(exception).isNotNull();
        assertThat(webRequest).isNotNull();

        // when:
        var problemDetail = testedProblemFactory.getValidationError(exception, webRequest);

        // then:
        assertThat(problemDetail.getType())
            .isEqualTo(URI.create("/problems/validation-failed"));
        assertThat(problemDetail.getTitle())
            .isEqualTo("Request Validation Failed");
        assertThat(problemDetail.getStatus())
            .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getDetail())
            .isEqualTo("Request has one or more validation errors. Please fix them and try again.");

        Map<String, Object> expectedErrorDetails = new HashMap<>();
        expectedErrorDetails.put("in", expectedIn);
        expectedErrorDetails.put("name", expectedName);
        expectedErrorDetails.put("path", expectedName);
        expectedErrorDetails.put("rejectedValue", expectedRejectedValue);
        expectedErrorDetails.put("message", expectedMessage);

        Map<String, List<Map<String, Object>>> expected =
            Map.of(
                "errors",
                List.of(
                    expectedErrorDetails
                )
            );

        assertThat(problemDetail.getProperties()).isEqualTo(expected);
    }

    static Stream<Arguments> singleCasesProvider() {
        return Stream.of(
            arguments(
                "/test/noname",
                "i",
                List.of("name"),
                "header",
                "path",
                "id",
                "i",
                "size must be between 2 and 5"
            ),
            arguments(
                "/test/noname",
                "id",
                List.of("    "),
                "header",
                "query",
                "name",
                "    ",
                "must not be blank"
            ),
            arguments(
                "/test/noname",
                "id",
                List.of("name"),
                "longHeader",
                "header",
                "header",
                "longHeader",
                "size must be between 1 and 6"
            ),
            arguments(
                "/test/renamed",
                "i",
                List.of("name"),
                "header",
                "path",
                "id",
                "i",
                "size must be between 2 and 5"
            ),
            arguments(
                "/test/renamed",
                "id",
                List.of("    "),
                "header",
                "query",
                "name",
                "    ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed",
                "id",
                List.of("name"),
                "longHeader",
                "header",
                "header",
                "longHeader",
                "size must be between 1 and 6"
            ),
            arguments(
                "/test/noname/list",
                "id",
                List.of("name1", "   ", "name3"),
                "header",
                "query",
                "name[1]",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed/list",
                "id",
                List.of("name1", "   ", "name3"),
                "header",
                "query",
                "name[1]",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/noname/set",
                "id",
                List.of("   ", "name2", "name3"),
                "header",
                "query",
                "name[]",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed/set",
                "id",
                List.of("   ", "name2", "name3"),
                "header",
                "query",
                "name[]",
                "   ",
                "must not be blank"
            )
        );
    }

    @ParameterizedTest
    @MethodSource("multipleCasesProvider")
    public void multipleModelAttributeIncorrect(final String baseUrl,
                                                final String incorrectIdParameter,
                                                final List<String> incorrectNameParameters,
                                                final String incorrectHeaderParameter,
                                                final String expectedIdMessage,
                                                final String expectedNameValue,
                                                final String expectedNamePath,
                                                final String expectedNameMessage,
                                                final Object expectedHeaderMessage) {
        var incorrectQueryParams = incorrectNameParameters.stream()
            .map(v -> "name=" + v)
            .collect(Collectors.joining("&", "?", ""));
        var url = baseUrl + "/%s%s".formatted(incorrectIdParameter, incorrectQueryParams);
        client.get()
            .uri(url)
            .header("header", incorrectHeaderParameter)
            .exchange();

        var exception = controllerAdvice.getMethodArgumentNotValidException();
        var webRequest = controllerAdvice.getWebRequest();
        assertThat(exception).isNotNull();

        // when:
        var problemDetail = testedProblemFactory.getValidationError(exception, webRequest);

        // then:
        assertThat(problemDetail.getType())
            .isEqualTo(URI.create("/problems/validation-failed"));
        assertThat(problemDetail.getTitle())
            .isEqualTo("Request Validation Failed");
        assertThat(problemDetail.getStatus())
            .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getDetail())
            .isEqualTo("Request has one or more validation errors. Please fix them and try again.");

        final List<Map<String, Object>> expectedErrorsList = List.of(
            Map.of(
                "in", "path",
                "name", "id",
                "path", "id",
                "rejectedValue", incorrectIdParameter,
                "message", expectedIdMessage
            ),
            Map.of(
                "in", "query",
                "name", expectedNamePath,
                "path", expectedNamePath,
                "rejectedValue", expectedNameValue,
                "message", expectedNameMessage
            ),
            Map.of(
                "in", "header",
                "name", "header",
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
                "/test/noname",
                "i",
                List.of("   "),
                "longHeader",
                "size must be between 2 and 5",
                "   ",
                "name",
                "must not be blank",
                "size must be between 1 and 6"
            ),
            arguments(
                "/test/renamed",
                "i",
                List.of("   "),
                "longHeader",
                "size must be between 2 and 5",
                "   ",
                "name",
                "must not be blank",
                "size must be between 1 and 6"
            ),
            arguments(
                "/test/noname/list",
                "i",
                List.of("   ", "name2", "name3"),
                "longHeader",
                "size must be between 2 and 5",
                "   ",
                "name[0]",
                "must not be blank",
                "size must be between 1 and 6"
            ),
            arguments(
                "/test/noname/set",
                "i",
                List.of("name1", "   ", "name3"),
                "longHeader",
                "size must be between 2 and 5",
                "   ",
                "name[]",
                "must not be blank",
                "size must be between 1 and 6"
            )
        );
    }

    @RestController
    @RequestMapping("/test")
    class TestModelAttributeController {

        @GetMapping("/noname/{id}")
        String getNoName(final @ModelAttribute @Valid Noname noname) {
            return "%s-%s-%s".formatted(noname.id, noname.name, noname.header);
        }

        @GetMapping("/renamed/{id}")
        String getRenamed(final @ModelAttribute @Valid Renamed renamed) {
            return "%s-%s-%s".formatted(renamed.identifier, renamed.fullName, renamed.headerValue);
        }

        @GetMapping("/noname/list/{id}")
        String getNoNameList(final @ModelAttribute @Valid NonameList noname) {
            return "%s-%s-%s".formatted(noname.id, noname.name, noname.header);
        }

        @GetMapping("/renamed/list/{id}")
        String getRenamedList(final @ModelAttribute @Valid RenamedList renamed) {
            return "%s-%s-%s".formatted(renamed.identifier, renamed.names, renamed.headerValue);
        }

        @GetMapping("/noname/set/{id}")
        String getNoNameSet(final @ModelAttribute @Valid NonameSet noname) {
            return "%s-%s-%s".formatted(noname.id, noname.name, noname.header);
        }

        @GetMapping("/renamed/set/{id}")
        String getRenamedSet(final @ModelAttribute @Valid RenamedSet renamed) {
            return "%s-%s-%s".formatted(renamed.identifier, renamed.names, renamed.headerValue);
        }
    }

    record Noname(
        @Size(min = 2, max = 5)
        String id,                                  // @PathVariable

        @NotBlank @Size(min = 1, max = 100)
        String name,                                // @RequestParam

        @NotBlank @Size(min = 1, max = 6)
        String header                               // @RequestHeader
    ){}

    record NonameList(
        @Size(min = 2, max = 5)
        String id,                                  // @PathVariable

        @NotEmpty
        List<@NotBlank @Size(min = 1, max = 100) String>
            name,                                   // @RequestParam

        @NotBlank @Size(min = 1, max = 6)
        String header                               // @RequestHeader
    ){}

    record NonameSet(
        @Size(min = 2, max = 5)
        String id,                                  // @PathVariable

        @NotEmpty
        Set<@NotBlank @Size(min = 1, max = 100) String>
            name,                                   // @RequestParam

        @NotBlank @Size(min = 1, max = 6)
        String header                               // @RequestHeader
    ){}

    record Renamed(
        @BindParam("id")
        @Size(min = 2, max = 5)
        String identifier,

        @BindParam("name")
        @NotBlank @Size(min = 1, max = 100)
        String fullName,

        @BindParam("header")
        @NotBlank @Size(min = 1, max = 6)
        String headerValue
    ){}

    record RenamedList(
        @BindParam("id")
        @Size(min = 2, max = 5)
        String identifier,

        @BindParam("name")
        @NotEmpty List<@NotBlank @Size(min = 1, max = 100) String>
            names,

        @BindParam("header")
        @NotBlank @Size(min = 1, max = 6)
        String headerValue
    ){}

    record RenamedSet(
        @BindParam("id")
        @Size(min = 2, max = 5)
        String identifier,

        @BindParam("name")
        @NotEmpty Set<@NotBlank @Size(min = 1, max = 100) String>
            names,

        @BindParam("header")
        @NotBlank @Size(min = 1, max = 6)
        String headerValue
    ){}

}
