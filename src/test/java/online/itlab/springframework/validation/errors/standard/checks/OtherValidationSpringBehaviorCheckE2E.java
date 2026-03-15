package online.itlab.springframework.validation.errors.standard.checks;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import online.itlab.springframework.validation.errors.standard.extension.JvseExceptionHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests that confirms the behavior for:
 * - @SessionAttribute
 * - @RequestAttribute
 *
 * @SessionAttribute
 * - (@SessionAttribute("name") @NotBlank String name) {}                    - validated
 * - (@SessionAttribute("employee") @NotNull @Valid Employee employee) {}    - validated
 * - (@SessionAttribute("employee") @Valid Employee employee) {}             - NOT validated
 *
 * @RequestAttribute
 * - (@RequestAttribute("requestName") @NotBlank String requestName)                  - validated
 * - (@RequestAttribute("requestEmployee") @NotNull @Valid Employee requestEmployee)  - validated
 * - (@RequestAttribute("requestEmployee") @Valid Employee requestEmployee)           - NOT validated
 */
@SpringBootTest(
    classes = OtherValidationSpringBehaviorCheckE2E.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "jvse.enabled=true"
    }
)
@AutoConfigureRestTestClient
public class OtherValidationSpringBehaviorCheckE2E {

    @Autowired
    private RestTestClient client;

    @ParameterizedTest
    @MethodSource("singleSessionAttributeCasesProvider")
    public void testSessionAttribute(final String readUrl,
                                     final HttpStatusCode expectedStatusCode) {

        var storeResult = client
            .post().uri("/other/session/store")
            .accept(MediaType.TEXT_PLAIN)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .returnResult();


        var jsessionid = storeResult.getResponseCookies().get("JSESSIONID");
        assertThat(jsessionid).isNotNull();

        var readResult = client
            .get().uri(readUrl)
            .accept(MediaType.APPLICATION_JSON)
            .cookie("JSESSIONID", jsessionid.get(0).getValue())
            .exchange()
            .expectStatus().isEqualTo(expectedStatusCode)
            .returnResult();
    }

    static Stream<Arguments> singleSessionAttributeCasesProvider() {
        return Stream.of(
            arguments(
                "/other/session/simple/read",
               HttpStatus.BAD_REQUEST
            ),
            arguments(
                "/other/session/object/read/triggered",
                HttpStatus.BAD_REQUEST
            ),
            arguments(
                "/other/session/object/read/regular",
                HttpStatus.OK
            )
        );
    }

    @ParameterizedTest
    @MethodSource("singleRequestAttributeCasesProvider")
    public void testRequestAttribute(final String readUrl,
                                     final HttpStatusCode expectedStatusCode) {

        var readResult = client
            .get().uri(readUrl)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isEqualTo(expectedStatusCode)
            .returnResult();
    }

    static Stream<Arguments> singleRequestAttributeCasesProvider() {
        return Stream.of(
            arguments(
                "/other/request/simple/read",
                HttpStatus.BAD_REQUEST
            ),
            arguments(
                "/other/request/object/read/triggered",
                HttpStatus.BAD_REQUEST
            ),
            arguments(
                "/other/request/object/read/regular",
                HttpStatus.OK
            )
        );
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(TestConfig.class)
    static class TestApplication {
    }

    @TestConfiguration
    public static class TestConfig {

        @RestController
        @RequestMapping("/other/session")
        public class OtherSessionTestController {

            @PostMapping("/store")
            @ResponseBody
            public String store(HttpSession session) {
                session.setAttribute("name", "   ");
                session.setAttribute("employee", new Employee(new Person("Alice", "W", 160), "CEO"));
                return "stored";
            }

            @GetMapping("/simple/read")
            @ResponseBody
            public String readSimple(@SessionAttribute("name") @NotBlank String name) {
                return name;
            }

            @GetMapping("/object/read/triggered")
            @ResponseBody
            public Employee readObjectTriggered(@SessionAttribute("employee") @NotNull @Valid Employee employee) {
                return employee;
            }

            @GetMapping("/object/read/regular")
            @ResponseBody
            public Employee readObjectRegular(@SessionAttribute("employee") @Valid Employee employee) {
                return employee;
            }
        }

        @RestController
        @RequestMapping("/other/request")
        public class OtherRequestTestController {

            @GetMapping("/simple/read")
            @ResponseBody
            public String readSimple(@RequestAttribute("requestName") @NotBlank String requestName) {
                return requestName;
            }

            @GetMapping("/object/read/triggered")
            @ResponseBody
            public Employee readObjectTriggered(@RequestAttribute("requestEmployee") @NotNull @Valid Employee requestEmployee) {
                return requestEmployee;
            }

            @GetMapping("/object/read/regular")
            @ResponseBody
            public Employee readObjectRegular(@RequestAttribute("requestEmployee") @Valid Employee requestEmployee) {
                return requestEmployee;
            }
        }

        @Component
        public class IncorrectFilter extends OncePerRequestFilter {
            @Override
            protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {

                request.setAttribute("requestName", "   ");
                request.setAttribute("requestEmployee", new Employee(new Person( "A", "Wonderland", 160), "CEO"));
                filterChain.doFilter(request, response);
            }
        }

        @RestControllerAdvice
        public class JakartaStandardExceptionHandler extends JvseExceptionHandler {}
    }

    record Employee(
        @Valid @NotNull Person person,
        @NotEmpty String position
    ){}

    record Person(
        @NotBlank @Size(min = 2, max = 100) String firstName,
        @NotBlank @Size(min = 2, max = 100) String lastName,
        @Positive Integer height
    ){}

}
