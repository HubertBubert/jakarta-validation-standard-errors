package online.itlab.springframework.validation.errors.standard.checks;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import online.itlab.springframework.validation.errors.standard.autoconfigure.JvseAutoConfiguration;
import online.itlab.springframework.validation.errors.standard.factory.IJakartaValidationProblemDetailFactory;
import online.itlab.springframework.validation.errors.standard.factory.helper.CapturingExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test checks if the message produced by the library is the same as specified in the README.md.
 * This prevents publication with the obsolete example.
 * If this test fails, the example in README.md needs to be updated.
 */
public class DocumentationExampleConsistencyCheck {

    RestTestClient client;
    CapturingExceptionHandler controllerAdvice;

    IJakartaValidationProblemDetailFactory testedProblemFactory;

    @BeforeEach
    public void setup() {
        // client configuration
        controllerAdvice = new CapturingExceptionHandler();
        client = RestTestClient
            .bindToController(new DocController())
            .configureServer(mockMvcBuilder -> {
                mockMvcBuilder.setControllerAdvice(controllerAdvice);
            }).build();

        // logic configuration
        JvseAutoConfiguration autoConfiguration = new JvseAutoConfiguration();
        testedProblemFactory = autoConfiguration.problemDetailFactory();
    }

    /*
    curl -X PATCH 'http://localhost:8080/temp/same/employees/99'  \
    -H "token: badToken" \
    -H "Content-Type: application/json" \
    -d '{
        "person": {
            "firstName": "A",
            "lastName":"Wonderland",
            "height": 0
        },
        "position": "main character"
    }'
     */

    @Test
    public void docTest() {
        var result = client
            .patch().uri("/doc/people/99")
            .header("token", "badToken")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(PersonWithFriends.builder()
                .person(Person.builder()
                    .firstName("Alice")
                    .lastName("Wonderland")
                    .height(0)
                    .build()
                )
                .friends(List.of(
                    Person.builder()
                        .firstName("B")
                        .lastName("Blue")
                        .height(170)
                        .build()
                ))
                .build()
            )
            .exchange()
            .expectBody(ProblemDetail.class)
            .returnResult();

        var exception = controllerAdvice.getHandlerMethodValidationException();
        var webRequest = controllerAdvice.getWebRequest();
        assertThat(exception).isNotNull();
        assertThat(webRequest).isNotNull();

        // when:
        var problemDetail = testedProblemFactory.getValidationError(exception, webRequest);

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
                "rejectedValue", 99,
                "message", "must be greater than or equal to 100"
            ),
            Map.of(
                "in", "header",
                "name", "token",
                "path", "token",
                "rejectedValue", "badToken",
                "message", "size must be between 16 and 16"
            ),
            Map.of(
                "in", "body",
                "name", "height",
                "path", "person.height",
                "rejectedValue", 0,
                "message", "must be greater than 0"
            ),
            Map.of(
                "in", "body",
                "name", "firstName",
                "path", "friends[0].firstName",
                "rejectedValue", "B",
                "message", "size must be between 2 and 100"
            )
        );

        assertThat(problemDetail.getProperties()).hasSize(1);
        assertThat(problemDetail.getProperties()).containsOnlyKeys("errors");
        var actualErrorsList = (List<Map<String, Object>>) problemDetail.getProperties().get("errors");
        assertThat(actualErrorsList).containsExactlyInAnyOrderElementsOf(expectedErrorsList);
    }

    @RestController
    @RequestMapping("/doc")
    public class DocController {
        @PatchMapping("/people/{id}")
        PersonWithFriends patchEmployee(final @PathVariable @Min(100) Integer id,
                               final @RequestHeader @Size(min = 16, max=16) String token,
                               final @RequestBody @Valid PersonWithFriends personWithFriends) {
            return personWithFriends;
        }
    }

    @Builder
    record Person(
        @NotBlank @Size(min = 2, max = 100) String firstName,
        @NotBlank @Size(min = 2, max = 100) String lastName,
        @Positive Integer height
    ){}

    @Builder
    record PersonWithFriends(
        @Valid @NotNull Person person,
        @NotEmpty List<@Valid Person> friends
    ){}

    @Builder
    record Department(
        @Valid @NotNull Person employ,
        @NotEmpty String position
    ){}
}
