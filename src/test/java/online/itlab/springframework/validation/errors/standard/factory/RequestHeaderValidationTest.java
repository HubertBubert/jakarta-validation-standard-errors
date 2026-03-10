package online.itlab.springframework.validation.errors.standard.factory;

import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class RequestHeaderValidationTest {

    RestTestClient client;
    CapturingExceptionHandler controllerAdvice;

    IJakartaValidationProblemDetailFactory testedProblemFactory;

    @BeforeEach
    public void setup() {
        // client configuration
        controllerAdvice = new CapturingExceptionHandler();
        client = RestTestClient
            .bindToController(new TestRequestHeaderController())
            .configureServer(mockMvc -> {
                mockMvc.setControllerAdvice(controllerAdvice);
            }).build();

        // logic configuration
        LibAutoConfiguration autoConfiguration = new LibAutoConfiguration();
        testedProblemFactory = autoConfiguration.problemDetailFactory();
    }

    @ParameterizedTest
    @MethodSource("singleCasesProvider")
    public void singleRequestHeaderIncorrect(final String url,
                                             final String nameHeader,
                                             final int yearHeader,
                                             final String expectedName,
                                             final Object expectedRejectedValue,
                                             final String expectedMessage) {

        client.get()
            .uri(url)
            .header("name", nameHeader)
            .header("year", Integer.toString(yearHeader))
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

        Map<String, List<Map<String, Object>>> expected =
            Map.of(
                "errors",
                List.of(
                    Map.of(
                        "in", "header",
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
                "Alice",
                1700,
                "year",
                1700,
                "must be greater than or equal to 1900"
            ),
            arguments(
                "/test/same",
                "     ",
                1990,
                "name",
                "     ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed",
                "Alice",
                1700,
                "year",
                1700,
                "must be greater than or equal to 1900"
            ),
            arguments(
                "/test/renamed",
                "     ",
                1990,
                "name",
                "     ",
                "must not be blank"
            ),
            arguments(
                "/test/noname",
                "Alice",
                1700,
                "year",
                1700,
                "must be greater than or equal to 1900"
            ),
            arguments(
                "/test/noname",
                "     ",
                1990,
                "name",
                "     ",
                "must not be blank"
            )
        );
    }

    @ParameterizedTest
    @MethodSource("multipleCasesProvider")
    public void multipleRequestHeaderIncorrect(final String url,
                                               final String incorrectNameParameter,
                                               final int incorrectYearParameter,
                                               final String expectedNameParameterMessage,
                                               final Object expectedYearParameterMessage) {

        client.get()
            .uri(url)
            .header("name", incorrectNameParameter)
            .header("year", Integer.toString(incorrectYearParameter))
            .exchange();

        var exception = controllerAdvice.getHandlerMethodValidationException();
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

        List<Map<String, Object>> expectedErrorsList = List.of(
            Map.of(
                "in", "header",
                "name", "name",
                "path", "name",
                "rejectedValue", incorrectNameParameter,
                "message", expectedNameParameterMessage
            ),
            Map.of(
                "in", "header",
                "name", "year",
                "path", "year",
                "rejectedValue", incorrectYearParameter,
                "message", expectedYearParameterMessage
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
                1800,
                "must not be blank",
                "must be greater than or equal to 1900"
            ),
            arguments(
                "/test/renamed",
                "     ",
                1800,
                "must not be blank",
                "must be greater than or equal to 1900"
            ),
            arguments(
                "/test/noname",
                "     ",
                1800,
                "must not be blank",
                "must be greater than or equal to 1900"
            )
        );
    }

    @RestController
    @RequestMapping("/test")
    class TestRequestHeaderController {

        @GetMapping("/same")
        String getSameName(final @RequestHeader("name") @NotBlank @Size(min = 1, max = 100) String name,
                           final @RequestHeader("year") @Min(1900) int year) {
            return "%s-%s".formatted(name, year);
        }

        @GetMapping("/renamed")
        String getRenamed(final @RequestHeader("name") @NotBlank @Size(min = 1, max = 100) String nameFilter,
                          final @RequestHeader("year") @Min(1900) int minYear) {
            return "%s-%s".formatted(nameFilter, minYear);
        }

        @GetMapping("/noname")
        String getNoName(final @RequestHeader @NotBlank @Size(min = 1, max = 100) String name,
                         final @RequestHeader @Min(1900) int year) {
            return "%s-%s".formatted(name, year);
        }
    }
}
