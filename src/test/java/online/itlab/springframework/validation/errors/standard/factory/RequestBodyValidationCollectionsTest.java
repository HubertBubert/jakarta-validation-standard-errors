package online.itlab.springframework.validation.errors.standard.factory;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import online.itlab.springframework.validation.errors.standard.autoconfigure.LibAutoConfiguration;
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
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class RequestBodyValidationCollectionsTest {

    RestTestClient client;
    CapturingExceptionHandler controllerAdvice;

    IJakartaValidationProblemDetailFactory testedProblemFactory;

    @BeforeEach
    public void setup() {
        // client configuration
        controllerAdvice = new CapturingExceptionHandler();
        client = RestTestClient
            .bindToController(new TestRequestBodyCollectionsController())
            .configureServer(mockMvcBuilder -> {
                mockMvcBuilder.setControllerAdvice(controllerAdvice);
            }).build();

        // logic configuration
        LibAutoConfiguration autoConfiguration = new LibAutoConfiguration();
        testedProblemFactory = autoConfiguration.problemDetailFactory();
    }

    @ParameterizedTest
    @MethodSource({"collectionsProvider"})
    public void postCollections(final String url,
                                final Object body,
                                final String expectedName,
                                final String expectedPath,
                                final Object expectedRejectedValue,
                                final String expectedMessage) {
        client
            .post().uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .returnResult();

        var exception = controllerAdvice.getMethodArgumentNotValidException();
        var webRequest = controllerAdvice.getWebRequest();
        assertThat(exception).isNotNull();

        // when:
        var problemDetail = testedProblemFactory.getValidationError(exception, webRequest);

        // then:
        assertThat(problemDetail.getType()).isEqualTo(
            URI.create("/problems/validation-failed")
        );
        assertThat(problemDetail.getTitle()).isEqualTo(
            "Request Validation Failed"
        );
        assertThat(problemDetail.getStatus()).isEqualTo(
            HttpStatus.BAD_REQUEST.value()
        );
        assertThat(problemDetail.getDetail()).isEqualTo(
            "Request has one or more validation errors. Please fix them and try again."
        );

        Map<String, List<Map<String, Object>>> expected =
            Map.of(
                "errors",
                List.of(
                    Map.of(
                        "in", "body",
                        "name", expectedName,
                        "path", expectedPath,
                        "rejectedValue", expectedRejectedValue,
                        "message", expectedMessage
                    )
                )
            );

        assertThat(problemDetail.getProperties()).isEqualTo(expected);
    }

    static Stream<Arguments> collectionsProvider() {

        return Stream.of(
            arguments(
                "/test/same/list",
                new ListBody(
                    Collections.EMPTY_LIST
                ),
                "people",
                "people",
                Collections.EMPTY_LIST,
                "must not be empty"
            ),
            arguments(
                "/test/same/list",
                new ListBody(
                    List.of(
                        new Person("   ", "Ace"),
                        new Person("Betty", "Bad")
                    )
                ),
                "firstName",
                "people[0].firstName",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/same/list",
                new ListBody(
                    List.of(
                        new Person("Alice", "Ace"),
                        new Person("Betty", "   ")
                    )
                ),
                "lastName",
                "people[1].lastName",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed/list",
                new ListBodyRenamed(
                    Collections.EMPTY_LIST
                ),
                "p",
                "p",
                Collections.EMPTY_LIST,
                "must not be empty"
            ),
            arguments(
                "/test/renamed/list",
                new ListBodyRenamed(
                    List.of(
                        new PersonRenamed("   ", "Ace"),
                        new PersonRenamed("Betty", "Bad")
                    )
                ),
                "fn",
                "p[0].fn",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed/list",
                new ListBodyRenamed(
                    List.of(
                        new PersonRenamed("Alice", "Ace"),
                        new PersonRenamed("Betty", "   ")
                    )
                ),
                "ln",
                "p[1].ln",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/same/list/list",
                new ListListBody(
                    Collections.EMPTY_LIST
                ),
                "people",
                "people",
                Collections.EMPTY_LIST,
                "must not be empty"
            ),
            arguments(
                "/test/same/list/list",
                new ListListBody(
                    List.of(
                        List.of(
                            new Person("Alice", "Ace"),
                            new Person("Betty", "Block")
                        ),
                        Collections.EMPTY_LIST
                    )
                ),
                "people[1]",
                "people[1]",
                Collections.EMPTY_LIST,
                "must not be empty"
            ),
            arguments(
                "/test/same/list/list",
                new ListListBody(
                    List.of(
                        List.of(
                            new Person("Alice", "Ace"),
                            new Person("   ", "Block")
                        ),
                        List.of(
                            new Person("Cathy", "Clock"),
                            new Person("Danny", "Door")
                        )
                    )
                ),
                "firstName",
                "people[0][1].firstName",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/same/list/list",
                new ListListBody(
                    List.of(
                        List.of(
                            new Person("Alice", "Ace"),
                            new Person("Betty", "Block")
                        ),
                        List.of(
                            new Person("Cathy", "   "),
                            new Person("Danny", "Door")
                        )
                    )
                ),
                "lastName",
                "people[1][0].lastName",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed/list/list",
                new ListListBodyRenamed(
                    Collections.EMPTY_LIST
                ),
                "p",
                "p",
                Collections.EMPTY_LIST,
                "must not be empty"
            ),
            arguments(
                "/test/renamed/list/list",
                new ListListBodyRenamed(
                    List.of(
                        List.of(
                            new PersonRenamed("Alice", "Ace"),
                            new PersonRenamed("Betty", "Block")
                        ),
                        Collections.EMPTY_LIST
                    )
                ),
                "p[1]",
                "p[1]",
                Collections.EMPTY_LIST,
                "must not be empty"
            ),
            arguments(
                "/test/renamed/list/list",
                new ListListBodyRenamed(
                    List.of(
                        List.of(
                            new PersonRenamed("Alice", "Ace"),
                            new PersonRenamed("   ", "Block")
                        ),
                        List.of(
                            new PersonRenamed("Cathy", "Clock"),
                            new PersonRenamed("Danny", "Door")
                        )
                    )
                ),
                "fn",
                "p[0][1].fn",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed/list/list",
                new ListListBodyRenamed(
                    List.of(
                        List.of(
                            new PersonRenamed("Alice", "Ace"),
                            new PersonRenamed("Betty", "Block")
                        ),
                        List.of(
                            new PersonRenamed("Cathy", "   "),
                            new PersonRenamed("Danny", "Door")
                        )
                    )
                ),
                "ln",
                "p[1][0].ln",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/same/list/map",
                new ListMapBody(
                    Collections.EMPTY_LIST
                ),
                "people",
                "people",
                Collections.EMPTY_LIST,
                "must not be empty"
            ),
            arguments(
                "/test/same/list/map",
                new ListMapBody(
                    List.of(
                        Map.of(
                            "one", new Person("Alice", "Ace"),
                            "two", new Person("Betty", "Block")
                        ),
                        Collections.EMPTY_MAP
                    )
                ),
                "people[1]",
                "people[1]",
                Collections.EMPTY_MAP,
                "must not be empty"
            ),
            arguments(
                "/test/same/list/map",
                new ListMapBody(
                    List.of(
                        Map.of(
                            "one", new Person("Alice", "Ace"),
                            "two", new Person("   ", "Block")
                        ),
                        Map.of(
                            "three", new Person("Cathy", "Clock"),
                            "four", new Person("Darline", "Dark")
                        )
                    )
                ),
                "firstName",
                "people[0][two].firstName",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/same/list/map",
                new ListMapBody(
                    List.of(
                        Map.of(
                            "one", new Person("Alice", "Ace"),
                            "two", new Person("Betty", "Block")
                        ),
                        Map.of(
                            "three", new Person("Cathy", "Clock"),
                            "four", new Person("Darline", "   ")
                        )
                    )
                ),
                "lastName",
                "people[1][four].lastName",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed/list/map",
                new ListMapBodyRenamed(
                    Collections.EMPTY_LIST
                ),
                "p",
                "p",
                Collections.EMPTY_LIST,
                "must not be empty"
            ),
            arguments(
                "/test/renamed/list/map",
                new ListMapBodyRenamed(
                    List.of(
                        Map.of(
                            "one", new PersonRenamed("Alice", "Ace"),
                            "two", new PersonRenamed("Betty", "Block")
                        ),
                        Collections.EMPTY_MAP
                    )
                ),
                "p[1]",
                "p[1]",
                Collections.EMPTY_MAP,
                "must not be empty"
            ),
            arguments(
                "/test/renamed/list/map",
                new ListMapBodyRenamed(
                    List.of(
                        Map.of(
                            "one", new PersonRenamed("Alice", "Ace"),
                            "two", new PersonRenamed("   ", "Block")
                        ),
                        Map.of(
                            "three", new PersonRenamed("Cathy", "Clock"),
                            "four", new PersonRenamed("Darline", "Dark")
                        )
                    )
                ),
                "fn",
                "p[0][two].fn",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed/list/map",
                new ListMapBodyRenamed(
                    List.of(
                        Map.of(
                            "one", new PersonRenamed("Alice", "Ace"),
                            "two", new PersonRenamed("Betty", "Block")
                        ),
                        Map.of(
                            "three", new PersonRenamed("Cathy", "Clock"),
                            "four", new PersonRenamed("Darline", "   ")
                        )
                    )
                ),
                "ln",
                "p[1][four].ln",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/same/map",
                new MapBody(
                    Collections.EMPTY_MAP
                ),
                "mappedPeople",
                "mappedPeople",
                Collections.EMPTY_MAP,
                "must not be empty"
            ),
            arguments(
                "/test/same/map",
                new MapBody(
                    Map.of(
                        "one", new Person("   ", "Ace"),
                        "two", new Person("Betty", "Bad")
                    )
                ),
                "firstName",
                "mappedPeople[one].firstName",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/same/map",
                new MapBody(
                    Map.of(
                        "one", new Person("Alice", "Ace"),
                        "two", new Person("Betty", "   ")
                    )
                ),
                "lastName",
                "mappedPeople[two].lastName",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed/map",
                new MapBodyRenamed(
                    Collections.EMPTY_MAP
                ),
                "mp",
                "mp",
                Collections.EMPTY_MAP,
                "must not be empty"
            ),
            arguments(
                "/test/renamed/map",
                new MapBodyRenamed(
                    Map.of(
                        "one", new PersonRenamed("   ", "Ace"),
                        "two", new PersonRenamed("Betty", "Bad")
                    )
                ),
                "fn",
                "mp[one].fn",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed/map",
                new MapBodyRenamed(
                    Map.of(
                        "one", new PersonRenamed("Alice", "Ace"),
                        "two", new PersonRenamed("Betty", "   ")
                    )
                ),
                "ln",
                "mp[two].ln",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/same/map/map",
                new MapMapBody(
                    Collections.EMPTY_MAP
                ),
                "mappedPeople",
                "mappedPeople",
                Collections.EMPTY_MAP,
                "must not be empty"
            ),
            arguments(
                "/test/same/map/map",
                new MapMapBody(
                    Map.of(
                        "engineers", Map.of(
                            "one", new Person("Alice", "Ace")
                        ),
                        "managers", Collections.EMPTY_MAP
                    )
                ),
                "mappedPeople[managers]",
                "mappedPeople[managers]",
                Collections.EMPTY_MAP,
                "must not be empty"
            ),
            arguments(
                "/test/same/map/map",
                new MapMapBody(
                    Map.of(
                        "engineers", Map.of(
                            "one", new Person("Alice", "Ace"),
                            "two", new Person("   ", "Block")
                        ),
                        "managers", Map.of(
                            "one", new Person("Cathy", "Clock"),
                            "two", new Person("Darline", "Dark")
                        )
                    )
                ),
                "firstName",
                "mappedPeople[engineers][two].firstName",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/same/map/map",
                new MapMapBody(
                    Map.of(
                        "engineers", Map.of(
                            "one", new Person("Alice", "Ace"),
                            "two", new Person("Betty", "Block")
                        ),
                        "managers", Map.of(
                            "one", new Person("Cathy", "   "),
                            "two", new Person("Darline", "Dark")
                        )
                    )
                ),
                "lastName",
                "mappedPeople[managers][one].lastName",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed/map/map",
                new MapMapBodyRenamed(
                    Collections.EMPTY_MAP
                ),
                "mp",
                "mp",
                Collections.EMPTY_MAP,
                "must not be empty"
            ),
            arguments(
                "/test/renamed/map/map",
                new MapMapBodyRenamed(
                    Map.of(
                        "engineers", Map.of(
                            "one", new PersonRenamed("Alice", "Ace")
                        ),
                        "managers", Collections.EMPTY_MAP
                    )
                ),
                "mp[managers]",
                "mp[managers]",
                Collections.EMPTY_MAP,
                "must not be empty"
            ),
            arguments(
                "/test/renamed/map/map",
                new MapMapBodyRenamed(
                    Map.of(
                        "engineers", Map.of(
                            "one", new PersonRenamed("Alice", "Ace"),
                            "two", new PersonRenamed("   ", "Block")
                        ),
                        "managers", Map.of(
                            "one", new PersonRenamed("Cathy", "Clock"),
                            "two", new PersonRenamed("Darline", "Dark")
                        )
                    )
                ),
                "fn",
                "mp[engineers][two].fn",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed/map/map",
                new MapMapBodyRenamed(
                    Map.of(
                        "engineers", Map.of(
                            "one", new PersonRenamed("Alice", "Ace"),
                            "two", new PersonRenamed("Betty", "Block")
                        ),
                        "managers", Map.of(
                            "one", new PersonRenamed("Cathy", "   "),
                            "two", new PersonRenamed("Darline", "Dark")
                        )
                    )
                ),
                "ln",
                "mp[managers][one].ln",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/same/map/list",
                new MapListBody(
                    Collections.EMPTY_MAP
                ),
                "mappedPeople",
                "mappedPeople",
                Collections.EMPTY_MAP,
                "must not be empty"
            ),
            arguments(
                "/test/same/map/list",
                new MapListBody(
                    Map.of(
                        "engineers", List.of(
                            new Person("Alice", "Ace"),
                            new Person("Betty", "Block")
                        ),
                        "managers", Collections.EMPTY_LIST
                    )
                ),
                "mappedPeople[managers]",
                "mappedPeople[managers]",
                Collections.EMPTY_LIST,
                "must not be empty"
            ),
            arguments(
                "/test/same/map/list",
                new MapListBody(
                    Map.of(
                        "engineers", List.of(
                            new Person("Alice", "Ace"),
                            new Person("   ", "Block")
                        ),
                        "managers", List.of(
                            new Person("Cathy", "Clock"),
                            new Person("Danny", "Door")
                        )
                    )
                ),
                "firstName",
                "mappedPeople[engineers][1].firstName",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/same/map/list",
                new MapListBody(
                    Map.of(
                        "engineers", List.of(
                            new Person("Alice", "Ace"),
                            new Person("Betty", "Block")
                        ),
                        "managers", List.of(
                            new Person("Cathy", "   "),
                            new Person("Danny", "Door")
                        )
                    )
                ),
                "lastName",
                "mappedPeople[managers][0].lastName",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed/map/list",
                new MapListBodyRenamed(
                    Collections.EMPTY_MAP
                ),
                "mp",
                "mp",
                Collections.EMPTY_MAP,
                "must not be empty"
            ),
            arguments(
                "/test/renamed/map/list",
                new MapListBodyRenamed(
                    Map.of(
                        "engineers", List.of(
                            new PersonRenamed("Alice", "Ace"),
                            new PersonRenamed("Betty", "Block")
                        ),
                        "managers", Collections.EMPTY_LIST
                    )
                ),
                "mp[managers]",
                "mp[managers]",
                Collections.EMPTY_LIST,
                "must not be empty"
            ),
            arguments(
                "/test/renamed/map/list",
                new MapListBodyRenamed(
                    Map.of(
                        "engineers", List.of(
                            new PersonRenamed("Alice", "Ace"),
                            new PersonRenamed("   ", "Block")
                        ),
                        "managers", List.of(
                            new PersonRenamed("Cathy", "Clock"),
                            new PersonRenamed("Danny", "Door")
                        )
                    )
                ),
                "fn",
                "mp[engineers][1].fn",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed/map/list",
                new MapListBodyRenamed(
                    Map.of(
                        "engineers", List.of(
                            new PersonRenamed("Alice", "Ace"),
                            new PersonRenamed("Betty", "Block")
                        ),
                        "managers", List.of(
                            new PersonRenamed("Cathy", "   "),
                            new PersonRenamed("Danny", "Door")
                        )
                    )
                ),
                "ln",
                "mp[managers][0].ln",
                "   ",
                "must not be blank"
            )
        );
    }

    public static void main(String ...args) {
        var o = new ObjectMapper();
        System.out.println(o.writeValueAsString(new ListBody(
            Collections.EMPTY_LIST
        )));
        System.out.println(o.writeValueAsString(new ListBody(
            Collections.EMPTY_LIST
        )));
    }

    @RestController
    @RequestMapping("/test")
    static class TestRequestBodyCollectionsController {

        @PostMapping("/same/list")
        ListBody postSameList(final @RequestBody @Valid ListBody listBody) {
            return listBody;
        }

        @PostMapping("/renamed/list")
        ListBodyRenamed postRenamedList(final @RequestBody @Valid ListBodyRenamed listBody) {
            return listBody;
        }

        @PostMapping("/same/list/list")
        ListListBody postSameListList(final @RequestBody @Valid ListListBody listBody) {
            return listBody;
        }

        @PostMapping("/renamed/list/list")
        ListListBodyRenamed postRenamedList(final @RequestBody @Valid ListListBodyRenamed listBody) {
            return listBody;
        }

        @PostMapping("/same/list/map")
        ListMapBody postSameListList(final @RequestBody @Valid ListMapBody listBody) {
            return listBody;
        }

        @PostMapping("/renamed/list/map")
        ListMapBodyRenamed postSameListList(final @RequestBody @Valid ListMapBodyRenamed listBody) {
            return listBody;
        }

        @PostMapping("/same/map")
        MapBody postSameMap(final @RequestBody @Valid MapBody mapBody) {
            return mapBody;
        }

        @PostMapping("/renamed/map")
        MapBodyRenamed postRenamedMap(final @RequestBody @Valid MapBodyRenamed mapBody) {
            return mapBody;
        }

        @PostMapping("/same/map/map")
        MapMapBody postSameMap(final @RequestBody @Valid MapMapBody mapBody) {
            return mapBody;
        }

        @PostMapping("/renamed/map/map")
        MapMapBodyRenamed postRenamedMap(final @RequestBody @Valid MapMapBodyRenamed mapBody) {
            return mapBody;
        }

        @PostMapping("/same/map/list")
        MapListBody postSameMap(final @RequestBody @Valid MapListBody mapBody) {
            return mapBody;
        }

        @PostMapping("/renamed/map/list")
        MapListBodyRenamed postRenamedMap(final @RequestBody @Valid MapListBodyRenamed mapBody) {
            return mapBody;
        }
    }

    record Person(
        @NotBlank String firstName,
        @NotBlank String lastName
    ){}

    record ListBody(
        @NotEmpty List<@Valid Person> people
    ){}

    record ListListBody(
        @NotEmpty List<@NotEmpty List<@Valid Person>> people
    ){}

    record ListMapBody(
        @NotEmpty List<@NotEmpty Map<@NotBlank String, @Valid Person>> people
    ){}

    record MapBody(
        @NotEmpty Map<@NotBlank String, @Valid Person> mappedPeople
    ){}

    record MapMapBody(
        @NotEmpty Map<@NotBlank String, @NotEmpty Map<@NotBlank String, @Valid Person>> mappedPeople
    ){}

    record MapListBody(
        @NotEmpty Map<@NotBlank String, @NotEmpty List<@Valid Person>> mappedPeople
    ){}

    record PersonRenamed(
        @JsonProperty("fn") @NotBlank String firstName,
        @JsonProperty("ln") @NotBlank String lastName
    ){}

    record ListBodyRenamed(
        @JsonProperty("p") @NotEmpty List<@Valid PersonRenamed> people
    ){}

    record ListListBodyRenamed(
        @JsonProperty("p") @NotEmpty List<@NotEmpty List<@Valid PersonRenamed>> people
    ){}

    record ListMapBodyRenamed(
        @JsonProperty("p") @NotEmpty List<@NotEmpty Map<@NotBlank String, @Valid PersonRenamed>> people
    ){}

    record MapBodyRenamed(
        @JsonProperty("mp") @NotEmpty Map<@NotBlank String, @Valid PersonRenamed> mappedPeople
    ){}

    record MapMapBodyRenamed(
        @JsonProperty("mp") @NotEmpty Map<@NotBlank String, @NotEmpty Map<@NotBlank String, @Valid PersonRenamed>> mappedPeople
    ){}

    record MapListBodyRenamed(
        @JsonProperty("mp") @NotEmpty Map<@NotBlank String, @NotEmpty List<@Valid PersonRenamed>> mappedPeople
    ){}
}