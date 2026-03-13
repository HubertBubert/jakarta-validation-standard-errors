package online.itlab.springframework.validation.errors.standard.factory;

import jakarta.validation.constraints.Size;
import online.itlab.springframework.validation.errors.standard.autoconfigure.JvseAutoConfiguration;
import online.itlab.springframework.validation.errors.standard.configuration.JvseConfig;
import online.itlab.springframework.validation.errors.standard.factory.helper.CapturingExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class RequestPartValidationTest {

    private CapturingExceptionHandler controllerAdvice;
    private MockMvc mockMvc;

    private IJakartaValidationProblemDetailFactory testedProblemFactory;

    @BeforeEach
    void setup() {
        // client configuration
        controllerAdvice = new CapturingExceptionHandler();
        this.mockMvc = MockMvcBuilders
            .standaloneSetup(new TestRequestPartController())
            .setControllerAdvice(controllerAdvice)
            .build();

        // logic configuration
        JvseAutoConfiguration autoConfiguration = new JvseAutoConfiguration();
        testedProblemFactory = autoConfiguration.problemDetailFactory(new JvseConfig());
    }

    @ParameterizedTest
    @MethodSource("singleMissingCasesProvider")
    void singleMissingRequestPartIncorrect(String url, String disabledPart) throws Exception {
        String fileContent = "my local file content";

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test-file.txt",
            MediaType.TEXT_PLAIN_VALUE,
            fileContent.getBytes()
        );

        MockMultipartFile description = new MockMultipartFile(
            "description",
            "",
            MediaType.TEXT_PLAIN_VALUE,
            "My test file description".getBytes()
        );

        var requestBuilder = multipart(url);
        if (!disabledPart.equals("file")) {
            requestBuilder.file(file);
        }
        if (!disabledPart.equals("description")) {
            requestBuilder.file(description);
        }

        mockMvc.perform(requestBuilder)
            .andExpect(status().isBadRequest());

        var exception = controllerAdvice.getMissingServletRequestPartException();
        assertThat(exception).isNotNull();

        // when:
        var problemDetail = testedProblemFactory.getValidationError(exception);

        // then:
        assertThat(problemDetail.getType())
            .isEqualTo(URI.create("/problems/validation-failed"));
        assertThat(problemDetail.getTitle())
            .isEqualTo("Request Validation Failed");
        assertThat(problemDetail.getStatus())
            .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getDetail())
            .isEqualTo("Request has one or more validation errors. Please fix them and try again.");

        final Map<String, Object> errorDetailsMap = new HashMap<>();
        errorDetailsMap.put("in", "part");
        errorDetailsMap.put("name", disabledPart);
        errorDetailsMap.put("path", disabledPart);
        errorDetailsMap.put("rejectedValue", null);
        errorDetailsMap.put("message", "Required part is not present.");

        Map<String, List<Map<String, Object>>> expected =
            Map.of(
                "errors",
                List.of(
                    errorDetailsMap
                )
            );

        assertThat(problemDetail.getProperties()).isEqualTo(expected);
    }

    static Stream<Arguments> singleMissingCasesProvider() {
        return Stream.of(
            arguments(
                "/test/same",
                "file"
            ),
            arguments(
                "/test/same",
                "file"
            )
        );
    }

    @Disabled("Before reaching visitor field an exception is thrown on the exception itself - looks like this is incorrect")
    @ParameterizedTest
    @MethodSource("singleCasesProvider")
    void singleRequestPartIncorrect(final String url, final String descriptionValue) throws Exception {
        String fileContent = "my local file content";

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test-file.txt",
            MediaType.TEXT_PLAIN_VALUE,
            fileContent.getBytes()
        );

        MockMultipartFile description = new MockMultipartFile(
            "description",
            "",
            MediaType.TEXT_PLAIN_VALUE,
            descriptionValue.getBytes()
        );

        mockMvc
            .perform(
                multipart(url)
                    .file(file)
                    .file(description)
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

        Map<String, List<Map<String, String>>> expected =
            Map.of(
                "errors",
                List.of(
                    Map.of(
                        "in", "part",
                        "name", "description",
                        "path", "description",
                        "rejectedValue", descriptionValue,
                        "message", "size must be between 5 and 10"
                    )
                )
            );

        assertThat(problemDetail.getProperties()).isEqualTo(expected);
    }

    static Stream<Arguments> singleCasesProvider() {
        return Stream.of(
            arguments(
                "/test/same",
                "valu"
            )
        );
    }

    @RestController
    @RequestMapping("/test")
    class TestRequestPartController {

        @PostMapping(value = "/same", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public String upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart("description") @Size(min=5, max=10) String description) throws IOException {

            System.out.println("file");
            System.out.println("\tfilename: " + file.getOriginalFilename());
            System.out.println("\tcontent: " + new String(file.getBytes()));
            return file.getOriginalFilename() + ":'" + description + "'";
        }
    }
    // TODO
//    Some libraries add useful constraints like:
//    - @FileSize
//    - @FileType
//    Examples include:
//    - Hibernate Validator extensions
//    - Spring validation utilities
//    But they are not part of Jakarta Validation itself.
}
