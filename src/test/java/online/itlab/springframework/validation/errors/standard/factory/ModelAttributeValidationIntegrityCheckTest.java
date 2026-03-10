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
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.BindParam;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class ModelAttributeValidationIntegrityCheckTest {

    RestTestClient client;
    CapturingExceptionHandler controllerAdvice;

    IJakartaValidationProblemDetailFactory testedProblemFactory;

    @BeforeEach
    public void setup() {
        // client configuration
        controllerAdvice = new CapturingExceptionHandler();
        client = RestTestClient
            .bindToController(new TestModelAttributeIntegrityController())
            .configureServer(mockMvc -> {
                mockMvc.setControllerAdvice(controllerAdvice);
            }).build();

        // logic configuration
        LibAutoConfiguration autoConfiguration = new LibAutoConfiguration();
        testedProblemFactory = autoConfiguration.problemDetailFactory();
    }

    @ParameterizedTest
    @MethodSource("singleCasesProvider")
    public void singleModelAttributeIncorrect(final String baseUrl,
                                              final String idParameter,
                                              final String nameParameter,
                                              final String headerParameter,
                                              final String expectedIn) {

        // 1. Flow
        var urlOnly = baseUrl.formatted("single") + "/%s?name=%s".formatted(idParameter, nameParameter);
        var resultOnly = client.get()
            .uri(urlOnly)
            .header("header", headerParameter)
            .cookie("cookie", "cookieValue")
            .exchange()
            .returnResult();

        var onlyException = controllerAdvice.getMethodArgumentNotValidException();
        var onlyWebRequest = controllerAdvice.getWebRequest();
        assertNotNull(onlyException);
        assertNotNull(onlyWebRequest);

        var problemDetailOnly = testedProblemFactory.getValidationError(onlyException, onlyWebRequest);

        // 2. Flow
        controllerAdvice.reset();

        var urlMixed = baseUrl.formatted("mixed") + "/%s?name=%s&standaloneRequestParam=present".formatted(idParameter, nameParameter);
        var resultMixed = client.get()
            .uri(urlMixed)
            .header("header", headerParameter)
            .cookie("cookie", "cookieValue")
            .exchange()
            .returnResult();

        var mixedException = controllerAdvice.getHandlerMethodValidationException();
        var mixedWebRequest = controllerAdvice.getWebRequest();
        assertNotNull(mixedException);
        assertNotNull(mixedWebRequest);

        var problemDetailMixed = testedProblemFactory.getValidationError(mixedException, mixedWebRequest);

        // then
        var onlyErrorsList = (List<Map<String, Object>>) problemDetailOnly.getProperties().get("errors");
        var onlyErrorDetails = onlyErrorsList.stream()
            .filter(m -> expectedIn.equals(m.get("in")))
            .findFirst()
            .orElse(null);
        assertThat(onlyErrorDetails).isNotNull();

        var mixedErrorList = (List<Map<String, Object>>) problemDetailMixed.getProperties().get("errors");
        var mixedErrorDetails = mixedErrorList.stream()
            .filter(m -> expectedIn.equals(m.get("in")))
            .findFirst()
            .orElse(null);
        assertThat(mixedErrorDetails).isNotNull();

        assertThat(onlyErrorDetails).isEqualTo(mixedErrorDetails);
    }

    static Stream<Arguments> singleCasesProvider() {
        return Stream.of(
            arguments(
                "/test/%s/noname",
                "i",
                "Alice",
                "headerValue",
                "path"
            ),
            arguments(
                "/test/%s/noname",
                "id",
                "A",
                "headerValue",
                "query"
            ),
            arguments(
                "/test/%s/noname",
                "id",
                "Alice",
                "   ",
                "header"
            ),
            arguments(
                "/test/%s/renamed",
                "i",
                "Alice",
                "headerValue",
                "path"
            ),
            arguments(
                "/test/%s/renamed",
                "id",
                "A",
                "headerValue",
                "query"
            ),
            arguments(
                "/test/%s/renamed",
                "id",
                "Alice",
                "   ",
                "header"
            )
        );
    }


    @RestController
    @RequestMapping("/test")
    class TestModelAttributeIntegrityController {

        @GetMapping("/single/noname/{id}")
        String getSingleNoName(final @ModelAttribute @Valid Noname noname) {
            return "%s-%s-%s".formatted(noname.id, noname.name, noname.header);
        }

        @GetMapping("/single/renamed/{id}")
        String getSingleRenamed(final @ModelAttribute @Valid Renamed renamed) {
            return "%s-%s-%s".formatted(renamed.identifier, renamed.fullName, renamed.headerValue);
        }

        @GetMapping("/mixed/noname/{id}")
        String getMixedNoName(final @ModelAttribute @Valid Noname noname,
                              final @CookieValue @NotEmpty String cookie) {
            return "%s-%s-%s-%s".formatted(noname.id, noname.name, noname.header, cookie);
        }

        @GetMapping("/mixed/renamed/{id}")
        String getMixedRenamed(final @ModelAttribute @Valid Renamed renamed,
                               final @CookieValue @NotEmpty String cookie) {
            return "%s-%s-%s-%s".formatted(renamed.identifier, renamed.fullName, renamed.headerValue, cookie);
        }
    }

    record Noname(
        @Size(min = 2, max = 5)
        String id,                                  // @PathVariable

        @NotBlank @Size(min = 2, max = 100)
        String name,                                // @RequestParam

        @NotBlank @Size(min = 1, max = 15)
        String header                               // @RequestHeader
    ){}

    record Renamed(
        @BindParam("id")
        @Size(min = 2, max = 5)
        String identifier,

        @BindParam("name")
        @NotBlank @Size(min = 2, max = 100)
        String fullName,

        @BindParam("header")
        @NotBlank @Size(min = 1, max = 15)
        String headerValue
    ){}
}
