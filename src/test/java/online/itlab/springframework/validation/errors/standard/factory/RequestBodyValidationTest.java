package online.itlab.springframework.validation.errors.standard.factory;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import online.itlab.springframework.validation.errors.standard.factory.helper.CapturingExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class RequestBodyValidationTest {

    RestTestClient client;
    CapturingExceptionHandler controllerAdvice;

    IJakartaValidationProblemDetailFactory testedProblemFactory;

    @BeforeEach
    public void setup() {
        // client configuration
        controllerAdvice = new CapturingExceptionHandler();
        client = RestTestClient
            .bindToController(new TestRequestBodyController())
            .configureServer(mockMvcBuilder -> {
                mockMvcBuilder.setControllerAdvice(controllerAdvice);
            }).build();

        // logic configuration
        testedProblemFactory = new JakartaValidationProblemDetailFactory();
    }

    @ParameterizedTest
    @MethodSource({"personCasesProvider", "employeeCasesProvider"})
    public void testGetSpecificBook(final String urlPatter,
                                    final Object body,
                                    final String expectedPath,
                                    final String expectedRejectedValue,
                                    final String expectedMessage) {
        client
            .post().uri(urlPatter.formatted("same"))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .returnResult();

        var exception = controllerAdvice.getMethodArgumentNotValidException();
        assertNotNull(exception);

        // when:
        var problemDetail = testedProblemFactory.getValidationError(exception);

        // then:
        assertEquals(
            URI.create("/problems/validation-failed"),
            problemDetail.getType()
        );
        assertEquals(
            "Request Validation Failed",
            problemDetail.getTitle()
        );
        assertEquals(
            HttpStatus.BAD_REQUEST.value(),
            problemDetail.getStatus()
        );
        assertEquals(
            "Request has one or more validation errors. Please fix them and try again.",
            problemDetail.getDetail()
        );

        Map<String, List<Map<String, String>>> expected =
            Map.of(
                "errors",
                List.of(
                    Map.of(
                        "in", "body",
                        "path", expectedPath,
                        "rejectedValue", expectedRejectedValue,
                        "message", expectedMessage
                    )
                )
            );

        assertEquals(expected, problemDetail.getProperties());
    }

    @ParameterizedTest
    @MethodSource("employeeCasesProvider")
    public void testPostRenamed(final String urlPatter,
                                    final EmployeeSame employee ,
                                    final String expectedPath,
                                    final String expectedRejectedValue,
                                    final String expectedMessage) {
        var result = client
            .post().uri(urlPatter.formatted("renamed"))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body("""
                {
                    "pe": {
                        "fn": "%s",
                        "ln": "%s",
                        "h": %d
                    },
                    "po": "%s"
                }
                """.formatted(
                    employee.person.firstName,
                    employee.person.lastName,
                    employee.person.height,
                    employee.position
              ))
            .exchange()
            .returnResult();

        var exception = controllerAdvice.getMethodArgumentNotValidException();
        assertNotNull(exception);

        // when:
        var problemDetail = testedProblemFactory.getValidationError(exception);

        // then:
        assertEquals(
            URI.create("/problems/validation-failed"),
            problemDetail.getType()
        );
        assertEquals(
            "Request Validation Failed",
            problemDetail.getTitle()
        );
        assertEquals(
            HttpStatus.BAD_REQUEST.value(),
            problemDetail.getStatus()
        );
        assertEquals(
            "Request has one or more validation errors. Please fix them and try again.",
            problemDetail.getDetail()
        );

        Map<String, List<Map<String, String>>> expected =
            Map.of(
                "errors",
                List.of(
                    Map.of(
                        "in", "body",
                        "path", expectedPath,
                        "rejectedValue", expectedRejectedValue,
                        "message", expectedMessage
                    )
                )
            );

        assertEquals(expected, problemDetail.getProperties());
    }

    static Stream<Arguments> personCasesProvider() {
        return Stream.of(
            arguments(
                "/test/%s/people",
                new PersonSame("Alice", "Wonderland", -1),
                "height",
                "-1",
                "must be greater than 0"
            ),
            arguments(
                "/test/%s/people",
                new PersonSame("Alice", "    ", 160),
                "lastName",
                "    ",
                "must not be blank"
            )
        );
    }

    static Stream<Arguments> employeeCasesProvider() {
        return Stream.of(
            arguments(
                "/test/%s/employees",
                new EmployeeSame(new PersonSame("   ", "Wonderland", 160), "CEO"),
                "person.firstName",
                "   ",
                "must not be blank"
            )
        );
    }

    @RestController
    @RequestMapping("/test")
    static class TestRequestBodyController {

        @PostMapping("/same/employees")
        EmployeeSame createEmployee(final @RequestBody @Valid EmployeeSame employee) {
            return employee;
        }

        @PostMapping("/same/people")
        PersonSame createPerson(final @RequestBody @Valid PersonSame person) {
            return person;
        }

        @PostMapping("/renamed/employees")
        EmployeeRenamed createRenamedEmployee(final @RequestBody @Valid EmployeeRenamed employee) {
            return employee;
        }

        @PostMapping("/renamed/people")
        PersonRenamed createRenamedPerson(final @RequestBody @Valid PersonRenamed person) {
            return person;
        }
    }

    record PersonSame(
        @NotBlank @Size(min = 1, max = 100) String firstName,
        @NotBlank @Size(min = 1, max = 100) String lastName,
        @Positive Integer height
    ){}

    record EmployeeSame(
        @Valid @NotNull PersonSame person,
        @NotEmpty String position
    ){}

    record PersonRenamed(
        @JsonProperty("fn") @NotBlank @Size(min = 1, max = 100) String firstName,
        @JsonProperty("ln") @NotBlank @Size(min = 1, max = 100) String lastName,
        @JsonProperty("h") @Positive Integer height
    ){}

    record EmployeeRenamed(
        @JsonProperty("pe") @Valid @NotNull PersonRenamed person,
        @JsonProperty("po") @NotEmpty String position
    ){}
}