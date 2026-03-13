package online.itlab.springframework.validation.errors.standard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import online.itlab.springframework.validation.errors.standard.extension.StandardErrorsExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = JvseE2E.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "jvse.enabled=true"
    }
)
@AutoConfigureRestTestClient
public class JvseE2E {

    @Autowired
    private RestTestClient client;

    // triggers handleMethodArgumentNotValid()
    @Test
    public void testBodyOnly() {
        // when
        var result = client
            .post().uri("/e2e/bodyonly")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_PROBLEM_JSON)
            .body(Person.builder()
                .firstName("Alice")
                .lastName("Wonderland")
                .height(0)
                .build()
            )
            .exchange()
            .expectBody(ProblemDetail.class)
            .returnResult();

        var problemDetail = result.getResponseBody();

        // then
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
                        "in", "body",
                        "name", "height",
                        "path", "height",
                        "rejectedValue", 0,
                        "message", "must be greater than 0"
                    )
                )
            );

        assertThat(problemDetail.getProperties()).isEqualTo(expected);
    }

    // triggers handleHandlerMethodValidationException()
    @Test
    public void testMixed() {
        // when
        var result = client
            .patch().uri("/e2e/mixed/11")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_PROBLEM_JSON)
            .body(Person.builder()
                .firstName("   ")
                .lastName("Wonderland")
                .height(160)
                .build()
            )
            .exchange()
            .expectBody(ProblemDetail.class)
            .returnResult();

        var problemDetail = result.getResponseBody();

        // then
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
                        "in", "body",
                        "name", "firstName",
                        "path", "firstName",
                        "rejectedValue", "   ",
                        "message", "must not be blank"
                    )
                )
            );

        assertThat(problemDetail.getProperties()).isEqualTo(expected);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(TestConfig.class)
    static class TestApplication {
    }

    @TestConfiguration
    public static class TestConfig {

        @RestController
        @RequestMapping("/e2e")
        public static class TestE2eController {

            @PostMapping(value = "/bodyonly")
            Person postBodyOnly(final @RequestBody @Valid Person person) {
                return person;
            }

            @PatchMapping("/mixed/{id}")
            Person get(final @PathVariable("id") @Positive Long patchId,
                       final @RequestBody @Valid Person person) {
                return person;
            }
        }

        @RestControllerAdvice
        public class JakartaStandardExceptionHandler extends StandardErrorsExceptionHandler {}
    }

    @Builder
    record Person(
        @NotBlank @Size(min=2) String firstName,
        @NotBlank @Size(min=2) String lastName,
        @Positive Integer height
    ){}
}
