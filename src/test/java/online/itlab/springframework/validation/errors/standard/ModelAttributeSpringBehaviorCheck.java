package online.itlab.springframework.validation.errors.standard;

import jakarta.servlet.http.Cookie;
import lombok.Builder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test case which tests which request parts can be mapped to @ModelAttribute.
 * If this test fails it means the changes must be made to @ModelAttribute processing.
 */
public class ModelAttributeSpringBehaviorCheck {

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders
            .standaloneSetup(new TestModelAttributeSupport())
            .build();
    }

    @Test
    void singleMissingRequestPartIncorrect() throws Exception {

        // given
        final String pathVariableValue = "id1";
        final String requestParamValue = "paramValue";
        final String requestHeaderValue = "headerValue";
        final String requestPartFileValue = "fileContent";
        final String requestPartFileName = "file.txt";

        final String matrixVariableValue = "matrixValue";
        final String cookieValue = "cookieValue";

        MockMultipartFile file = new MockMultipartFile(
            "file",
            requestPartFileName,
            MediaType.TEXT_PLAIN_VALUE,
            requestPartFileValue.getBytes()
        );

        // when
        var result = mockMvc
            .perform(
                multipart("/test/%s;matrix=%s?query=%s".formatted(pathVariableValue, matrixVariableValue, requestParamValue))
                    .file(file)
                    .header("header", requestHeaderValue)
                    .cookie(new Cookie("cookie", cookieValue))
            )
            .andExpect(status().isOk())
            .andReturn();

        // then
        String jsonContent = result.getResponse().getContentAsString();
        Response response = new ObjectMapper().readValue(jsonContent, Response.class);

        // set @PathVariable, @RequestParam, @RequestHeader, @RequestPart
        assertThat(response.id).isEqualTo(pathVariableValue);
        assertThat(response.query).isEqualTo(requestParamValue);
        assertThat(response.header).isEqualTo(requestHeaderValue);
        assertThat(response.file.fileName).isEqualTo(requestPartFileName);
        assertThat(response.file.fileContents).isEqualTo(requestPartFileValue);

        // NOT set @CookieValue, @MatrixVariable
        assertThat(response.cookie).isNull();
        assertThat(response.matrix).isNull();
    }

    @RestController
    @RequestMapping("/test/{id}")
    class TestModelAttributeSupport {

        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public Response upload(final @ModelAttribute Request request) throws Exception {
            return Response.builder()
                .id(request.id)
                .query(request.query)
                .header(request.header)
                .file(ResponseFile.builder()
                    .fileName(request.file.getOriginalFilename())
                    .fileContents(new String(request.file.getBytes()))
                    .build()
                )
                .cookie(request.cookie)
                .matrix(request.matrix)
                .build();
        }
    }

    record Request(
        // supported
        String id,              // path variable
        String query,           // request param
        String header,          // header
        MultipartFile file,     // request part
        // NOT supported
        String cookie,          // cookie
        String matrix           // matrix variable
    ){}

    @Builder
    record Response(
        String id,
        String query,
        String header,
        ResponseFile file,
        String cookie,
        String matrix
    ){}

    @Builder
    record ResponseFile(
        String fileName,
        String fileContents
    ){}
}
