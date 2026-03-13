package online.itlab.springframework.validation.errors.standard.factory;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import online.itlab.springframework.validation.errors.standard.autoconfigure.JvseAutoConfiguration;
import online.itlab.springframework.validation.errors.standard.factory.helper.CapturingExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * This test ensures that error details returned for {@link RequestBody} are the same
 * regardless flow.
 */
public class RequestBodyValidationIntegrityCheckTest {

    RestTestClient client;
    CapturingExceptionHandler controllerAdvice;

    IJakartaValidationProblemDetailFactory testedProblemFactory;

    @BeforeEach
    public void setup() {
        // client configuration
        controllerAdvice = new CapturingExceptionHandler();
        client = RestTestClient
            .bindToController(new TestRequestBodyIntegrityController())
            .configureServer(mockMvcBuilder -> {
                mockMvcBuilder.setControllerAdvice(controllerAdvice);
            }).build();

        // logic configuration
        JvseAutoConfiguration autoConfiguration = new JvseAutoConfiguration();
        testedProblemFactory = autoConfiguration.problemDetailFactory();
    }

    @ParameterizedTest
    @MethodSource({"singleEmployeeCasesProvider"})
    public void testGetSpecificBook(final String urlPattern,
                                    final Object body) {
        // 1. Flow
        var urlOnlyBody = urlPattern.formatted("single");
        var onlyBodyResult = client
            .post().uri(urlOnlyBody)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectBody(ProblemDetail.class)
            .returnResult();

        var exceptionOnlyBody = controllerAdvice.getMethodArgumentNotValidException();
        var webRequestOnlyBody = controllerAdvice.getWebRequest();
        assertNotNull(exceptionOnlyBody);
        assertNotNull(webRequestOnlyBody);

        var problemDetailOnlyBody = testedProblemFactory.getValidationError(exceptionOnlyBody, webRequestOnlyBody);

        // 2. Flow
        controllerAdvice.reset();

        var urlMixed = urlPattern.formatted("mixed") + "/1";
        var mixedResult = client
            .patch().uri(urlMixed)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectBody(ProblemDetail.class)
            .returnResult();

        var exceptionMixed = controllerAdvice.getHandlerMethodValidationException();
        var webRequestMixed = controllerAdvice.getWebRequest();
        assertNotNull(exceptionMixed);
        assertNotNull(webRequestMixed);

        var problemDetailMixed = testedProblemFactory.getValidationError(exceptionMixed, webRequestMixed);

        // then
        var bodyOnlyErrorsList = (List<Map<String, Object>>) problemDetailOnlyBody.getProperties().get("errors");
        var bodyOnlyErrorDetails = bodyOnlyErrorsList.stream()
            .filter(m -> "body".equals(m.get("in")))
            .findFirst()
            .orElse(null);
        var mixedErrorList = (List<Map<String, Object>>) problemDetailMixed.getProperties().get("errors");
        var mixedBodyErrorDetails = mixedErrorList.stream()
            .filter(m -> "body".equals(m.get("in")))
            .findFirst()
            .orElse(null);

        assertThat(bodyOnlyErrorDetails).isEqualTo(mixedBodyErrorDetails);
    }

    static Stream<Arguments> singleEmployeeCasesProvider() {
        return Stream.of(
            arguments(
                "/test/%s/same/employees",
                new EmployeeSame(new PersonSame("   ", "Wonderland", 160), "CEO")
            ),
            arguments(
                "/test/%s/same/employees",
                new EmployeeSame(new PersonSame("Alice", "W", 160), "CEO")
            ),
            arguments(
                "/test/%s/same/employees",
                new EmployeeSame(new PersonSame("Alice", "Wonderland", 0), "CEO")
            ),
            arguments(
                "/test/%s/same/employees",
                new EmployeeSame(new PersonSame("Alice", "Wonderland", 160), "")
            ),
            arguments(
                "/test/%s/renamed/employees",
                new EmployeeRenamed(new PersonRenamed("   ", "Wonderland", 160), "CEO")
            ),
            arguments(
                "/test/%s/renamed/employees",
                new EmployeeRenamed(new PersonRenamed("Alice", "W", 160), "CEO")
            ),
            arguments(
                "/test/%s/renamed/employees",
                new EmployeeRenamed(new PersonRenamed("Alice", "Wonderland", 0), "CEO")
            ),
            arguments(
                "/test/%s/renamed/employees",
                new EmployeeRenamed(new PersonRenamed("Alice", "Wonderland", 160), "")
            )
        );
    }

    @RestController
    @RequestMapping("/test")
    static class TestRequestBodyIntegrityController {

        @PostMapping("/single/same/employees")
        EmployeeSame createEmployee(final @RequestBody @Valid EmployeeSame employee) {
            return employee;
        }

        @PostMapping("/single/renamed/employees")
        EmployeeRenamed createRenamedEmployee(final @RequestBody @Valid EmployeeRenamed employee) {
            return employee;
        }

        @PatchMapping("/mixed/same/employees/{employeeId}")
        EmployeeSame patchEmployee(final @PathVariable @Positive Integer employeeId,
                                   final @RequestBody @Valid EmployeeSame employee) {
            return employee;
        }

        @PatchMapping("/mixed/renamed/employees/{employeeId}")
        EmployeeRenamed patchRenamedEmployee(final @PathVariable @Positive Integer employeeId,
                                             final @RequestBody @Valid EmployeeRenamed employee) {
            return employee;
        }
    }

    record PersonSame(
        @NotBlank @Size(min = 2, max = 100) String firstName,
        @NotBlank @Size(min = 2, max = 100) String lastName,
        @Positive Integer height
    ){}

    record EmployeeSame(
        @Valid @NotNull PersonSame person,
        @NotEmpty String position
    ){}

    record PersonRenamed(
        @JsonProperty("fn") @NotBlank @Size(min = 2, max = 100) String firstName,
        @JsonProperty("ln") @NotBlank @Size(min = 2, max = 100) String lastName,
        @JsonProperty("h") @Positive Integer height
    ){}

    record EmployeeRenamed(
        @JsonProperty("pe") @Valid @NotNull PersonRenamed person,
        @JsonProperty("po") @NotEmpty String position
    ){}
}
