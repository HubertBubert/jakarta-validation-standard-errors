package online.itlab.springframework.validation.errors.standard.factory;

import jakarta.servlet.http.Cookie;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import online.itlab.springframework.validation.errors.standard.autoconfigure.JvseAutoConfiguration;
import online.itlab.springframework.validation.errors.standard.factory.helper.CapturingExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class CookieValueValidationTest {

    RestTestClient client;
    CapturingExceptionHandler controllerAdvice;

    IJakartaValidationProblemDetailFactory testedProblemFactory;

    @BeforeEach
    public void setup() {
        // client configuration
        controllerAdvice = new CapturingExceptionHandler();
        client = RestTestClient
            .bindToController(new TestCookieValueController())
            .configureServer(mockMvc -> {
                mockMvc.setControllerAdvice(controllerAdvice);
            }).build();

        // logic configuration
        JvseAutoConfiguration autoConfiguration = new JvseAutoConfiguration();
        testedProblemFactory = autoConfiguration.problemDetailFactory();
    }

    @ParameterizedTest
    @ValueSource(strings = { "/test/single/same", "/test/single/renamed", "/test/single/noname" })
    public void singleCookieValueIncorrect(final String url) {

        final var incorrectNameCookieValue = "     ";

        client.get()
            .uri(url)
            .cookie("name", incorrectNameCookieValue)
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
                        "in", "cookie",
                        "name", "name",
                        "path", "name",
                        "rejectedValue", incorrectNameCookieValue,
                        "message", "must not be blank"
                    )
                )
            );

        assertThat(problemDetail.getProperties()).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("singleCasesProvider")
    public void singleFromMultipleCookieValueIncorrect(final String url,
                                           final String nameCookieValue,
                                           final int yearCookieValue,
                                           final String expectedName,
                                           final Object expectedRejectedValue,
                                           final String expectedMessage) throws Exception {

        // does not set cookies correctly, so we need to use MockMvc
//        client.get()
//            .uri(url)
//            .cookie("year", Integer.toString(yearCookieValue))
//            .cookie("name", nameCookieValue)
//            .exchange();

        var mockMvc = MockMvcBuilders
            .standaloneSetup(new TestCookieValueController())
            .setControllerAdvice(controllerAdvice)
            .build();
        mockMvc.perform(
            get(url)
                .cookie(
                    new Cookie("name", nameCookieValue),
                    new Cookie("year", Integer.toString(yearCookieValue))
                )
            );

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
                        "in", "cookie",
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
                "/test/single/same",
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
    public void multipleCookieValueIncorrect(final String url,
                                             final String incorrectNameCookieValue,
                                             final int incorrectYearCookieValue,
                                             final String expectedNameCookieMessage,
                                             final Object expectedYearCookieMessage) throws Exception {

        // does not set cookies correctly, so we need to use MockMvc
//        client.get()
//            .uri(url)
//            .cookie("name", incorrectNameCookieValue)
//            .cookie("year", Integer.toString(incorrectYearCookieValue))
//            .exchange();

        var mockMvc = MockMvcBuilders
            .standaloneSetup(new TestCookieValueController())
            .setControllerAdvice(controllerAdvice)
            .build();
        mockMvc.perform(
            get(url)
                .cookie(
                    new Cookie("name", incorrectNameCookieValue),
                    new Cookie("year", Integer.toString(incorrectYearCookieValue))
                )
        );

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
                "in", "cookie",
                "name", "name",
                "path", "name",
                "rejectedValue", incorrectNameCookieValue,
                "message", expectedNameCookieMessage
            ),
            Map.of(
                "in", "cookie",
                "name", "year",
                "path", "year",
                "rejectedValue", incorrectYearCookieValue,
                "message", expectedYearCookieMessage
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
    class TestCookieValueController {

        @GetMapping("/single/same")
        String getSingleSameName(final @CookieValue("name") @NotBlank @Size(min = 1, max = 100) String name) {
            return name;
        }
        @GetMapping("/single/renamed")
        String getSingleRenamed(final @CookieValue("name") @NotBlank @Size(min = 1, max = 100) String nameFilter) {
            return nameFilter;
        }

        @GetMapping("/single/noname")
        String getSingleNoname(final @CookieValue @NotBlank @Size(min = 1, max = 100) String name) {
            return name;
        }

        @GetMapping("/same")
        String getSameName(final @CookieValue("name") @NotBlank @Size(min = 1, max = 100) String name,
                           final @CookieValue("year") @Min(1900) int year) {
            return "%s-%s".formatted(name, year);
        }

        @GetMapping("/renamed")
        String getRenamed(final @CookieValue("name") @NotBlank @Size(min = 1, max = 100) String nameFilter,
                          final @CookieValue("year") @Min(1900) int minYear) {
            return "%s-%s".formatted(nameFilter, minYear);
        }

        @GetMapping("/noname")
        String getNoName(final @CookieValue @NotBlank @Size(min = 1, max = 100) String name,
                         final @CookieValue @Min(1900) int year) {
            return "%s-%s".formatted(name, year);
        }
    }
}
