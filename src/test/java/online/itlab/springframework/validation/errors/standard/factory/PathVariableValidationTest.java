package online.itlab.springframework.validation.errors.standard.factory;

import jakarta.validation.constraints.Size;
import online.itlab.springframework.validation.errors.standard.autoconfigure.JvseAutoConfiguration;
import online.itlab.springframework.validation.errors.standard.configuration.JvseConfig;
import online.itlab.springframework.validation.errors.standard.factory.helper.CapturingExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class PathVariableValidationTest {

    RestTestClient client;
    CapturingExceptionHandler controllerAdvice;

    IJakartaValidationProblemDetailFactory testedProblemFactory;

    @BeforeEach
    public void setup() {
        // client configuration
        controllerAdvice = new CapturingExceptionHandler();
        client = RestTestClient
            .bindToController(new TestPathVariableController())
            .configureServer(mockMvc -> {
                mockMvc.setControllerAdvice(controllerAdvice);
            }).build();

        // logic configuration
        JvseAutoConfiguration autoConfiguration = new JvseAutoConfiguration();
        testedProblemFactory = autoConfiguration.problemDetailFactory(new JvseConfig());
    }

    @ParameterizedTest
    @MethodSource("singleCasesProvider")
    public void singlePathVariableIncorrect(final String baseUrl,
                                            final String mainId,
                                            final String nestedId,
                                            final String expectedRejectedValue,
                                            final String expectedName,
                                            final String expectedMessage) {

        var url = baseUrl + "/main/%s/nested/%s".formatted(mainId, nestedId);
        client.get()
            .uri(url)
            .exchange();

        var exception = controllerAdvice.getHandlerMethodValidationException();
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

        Map<String, List<Map<String, String>>> expected =
            Map.of(
                "errors",
                List.of(
                    Map.of(
                        "in", "path",
                        "name", expectedName,
                        "path", expectedName,
                        "rejectedValue", expectedRejectedValue,
                        "message", expectedMessage
                    )
                )
            );

        assertThat(problemDetail.getProperties()).isEqualTo(expected);
    }

    static Stream<Arguments> singleCasesProvider() {
        return Stream.of(
            arguments(
                "/test/same",
                "1",
                "nId",
                "1",
                "mainId",
                "size must be between 3 and 7"
            ),
            arguments(
                "/test/same",
                "123",
                "5",
                "5",
                "nestedId",
                "size must be between 2 and 5"
            ),
            arguments(
                "/test/renamed",
                "1",
                "nId",
                "1",
                "mainId",
                "size must be between 3 and 7"
            ),
            arguments(
                "/test/renamed",
                "123",
                "5",
                "5",
                "nestedId",
                "size must be between 2 and 5"
            ),
            arguments(
                "/test/noname",
                "1",
                "nId",
                "1",
                "id",
                "size must be between 3 and 7"
            ),
            arguments(
                "/test/noname",
                "123",
                "5",
                "5",
                "partId",
                "size must be between 2 and 5"
            )
        );
    }

    @ParameterizedTest
    @MethodSource("multipleCasesProvider")
    public void multiplePathVariableIncorrect(final String baseUrl,
                                              final String incorrectMainId,
                                              final String incorrectNestedId,
                                              final String expectedMainIdName,
                                              final String expectedNestedIdName) {

        var url = baseUrl + "/main/%s/nested/%s".formatted(incorrectMainId, incorrectNestedId);
        client.get()
            .uri(url)
            .exchange();

        var exception = controllerAdvice.getHandlerMethodValidationException();
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

        List<Map<String, Object>> expectedErrorsList = List.of(
            Map.of(
                "in", "path",
                "name", expectedMainIdName,
                "path", expectedMainIdName,
                "rejectedValue", incorrectMainId,
                "message", "size must be between 3 and 7"
            ),
            Map.of(
                "in", "path",
                "name", expectedNestedIdName,
                "path", expectedNestedIdName,
                "rejectedValue", incorrectNestedId,
                "message", "size must be between 2 and 5"
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
                "22",
                "1",
                "mainId",
                "nestedId"
            ),
            arguments(
                "/test/renamed",
                "22",
                "1",
                "mainId",
                "nestedId"
            ),
            arguments(
                "/test/noname",
                "22",
                "1",
                "id",
                "partId"
            )
        );
    }

    @RestController
    @RequestMapping("/test")
    class TestPathVariableController {

        @GetMapping("/same/{id}")
        String getSameName(@PathVariable("id") @Size(min = 2, max = 5) String id) {
            return "ok";
        }

        @GetMapping("/renamed/{id}")
        String getRenamed(@PathVariable("id") @Size(min = 2, max = 5) String testId) {
            return "ok";
        }

        @GetMapping("/noname/{testId}")
        String getNoName(@PathVariable @Size(min = 2, max = 5) String testId) {
            return "ok";
        }

        @GetMapping("/same/main/{mainId}/nested/{nestedId}")
        String getSame(final @PathVariable("mainId") @Size(min = 3, max = 7) String mainId,
                       final @PathVariable("nestedId") @Size(min = 2, max = 5) String nestedId) {
            return "ok";
        }

        @GetMapping("/renamed/main/{mainId}/nested/{nestedId}")
        String getRenamed(final @PathVariable("mainId") @Size(min = 3, max = 7) String id,
                       final @PathVariable("nestedId") @Size(min = 2, max = 5) String partId) {
            return "ok";
        }

        @GetMapping("/noname/main/{id}/nested/{partId}")
        String getNoName(final @PathVariable @Size(min = 3, max = 7) String id,
                         final @PathVariable @Size(min = 2, max = 5) String partId) {
            return "ok";
        }
    }
}
