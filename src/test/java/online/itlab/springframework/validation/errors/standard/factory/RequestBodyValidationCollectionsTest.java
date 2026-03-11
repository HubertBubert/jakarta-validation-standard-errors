package online.itlab.springframework.validation.errors.standard.factory;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
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

import java.math.BigDecimal;
import java.net.URI;
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
                "/test/same/finances",
                Account.builder()
                    .creditCards(List.of(
                        CreditCard.builder()
                            .availableBalance(new BigDecimal("9.49"))
                            .spendingByCategory(Map.of(
                                "food", new BigDecimal("3.49"),
                                "hobby", new BigDecimal("1.99"),
                                "apartment", new BigDecimal("5.99")
                            ))
                            .build()
                    ))
                    .loans(List.of(
                        Loan.builder()
                            .initialAmount(new BigDecimal("100.00"))
                            .currentAmount(new BigDecimal("49.00"))
                            .guarantors(List.of(
                                new Person("   ", "Ace"),
                                new Person("Bob", "Bale")
                            ))
                            .build()
                    ))
                    .access(Map.of(
                        AccessType.OWNER, List.of(
                            new Person("Sharon", "Stone")
                        ),
                        AccessType.FULL, List.of(
                            new Person("Caroline", "Cotton"),
                            new Person("David", "Diamond")
                        )
                    ))
                    .build(),
                "firstName",
                "loans[0].guarantors[0].firstName",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/same/finances",
                Account.builder()
                    .creditCards(List.of(
                        CreditCard.builder()
                            .availableBalance(new BigDecimal("9.49"))
                            .spendingByCategory(Map.of(
                                "food", new BigDecimal("3.49"),
                                "hobby", new BigDecimal("-1.99"),
                                "apartment", new BigDecimal("5.99")
                            ))
                            .build()
                    ))
                    .loans(List.of(
                        Loan.builder()
                            .initialAmount(new BigDecimal("100.00"))
                            .currentAmount(new BigDecimal("49.00"))
                            .guarantors(List.of(
                                new Person("Alice", "Ace"),
                                new Person("Bob", "Bale")
                            ))
                            .build()
                    ))
                    .access(Map.of(
                        AccessType.OWNER, List.of(
                            new Person("Sharon", "Stone")
                        ),
                        AccessType.FULL, List.of(
                            new Person("Caroline", "Cotton"),
                            new Person("David", "Diamond")
                        )
                    ))
                    .build(),
                "spendingByCategory[hobby]",
                "creditCards[0].spendingByCategory[hobby]",
                new BigDecimal("-1.99"),
                "must be greater than 0"
            ),
            arguments(
                "/test/renamed/finances",
                AccountRenamed.builder()
                    .creditCards(List.of(
                        CreditCardRenamed.builder()
                            .availableBalance(new BigDecimal("9.49"))
                            .spendingByCategory(Map.of(
                                "food", new BigDecimal("3.49"),
                                "hobby", new BigDecimal("1.99"),
                                "apartment", new BigDecimal("5.99")
                            ))
                            .build()
                    ))
                    .loans(List.of(
                        LoanRenamed.builder()
                            .initialAmount(new BigDecimal("100.00"))
                            .currentAmount(new BigDecimal("49.00"))
                            .guarantors(List.of(
                                new PersonRenamed("   ", "Ace"),
                                new PersonRenamed("Bob", "Bale")
                            ))
                            .build()
                    ))
                    .access(Map.of(
                        AccessType.OWNER, List.of(
                            new PersonRenamed("Sharon", "Stone")
                        ),
                        AccessType.FULL, List.of(
                            new PersonRenamed("Caroline", "Cotton"),
                            new PersonRenamed("David", "Diamond")
                        )
                    ))
                    .build(),
                "fn",
                "l[0].g[0].fn",
                "   ",
                "must not be blank"
            ),
            arguments(
                "/test/renamed/finances",
                AccountRenamed.builder()
                    .creditCards(List.of(
                        CreditCardRenamed.builder()
                            .availableBalance(new BigDecimal("9.49"))
                            .spendingByCategory(Map.of(
                                "food", new BigDecimal("3.49"),
                                "hobby", new BigDecimal("1.99"),
                                "apartment", new BigDecimal("-5.99")
                            ))
                            .build()
                    ))
                    .loans(List.of(
                        LoanRenamed.builder()
                            .initialAmount(new BigDecimal("100.00"))
                            .currentAmount(new BigDecimal("49.00"))
                            .guarantors(List.of(
                                new PersonRenamed("Alice", "Ace"),
                                new PersonRenamed("Bob", "Bale")
                            ))
                            .build()
                    ))
                    .access(Map.of(
                        AccessType.OWNER, List.of(
                            new PersonRenamed("Sharon", "Stone")
                        ),
                        AccessType.FULL, List.of(
                            new PersonRenamed("Caroline", "Cotton"),
                            new PersonRenamed("David", "Diamond")
                        )
                    ))
                    .build(),
                "sbc[apartment]",
                "cc[0].sbc[apartment]",
                new BigDecimal("-5.99"),
                "must be greater than 0"
            )
        );
    }

    @RestController
    @RequestMapping("/test")
    static class TestRequestBodyCollectionsController {

        @PostMapping("/same/finances")
        Account portSameAccount(final @RequestBody @Valid Account account) {
            return account;
        }

        @PostMapping("/renamed/finances")
        AccountRenamed postRenamedAccount(final @RequestBody @Valid AccountRenamed account) {
            return account;
        }
    }

    record Person(
        @NotBlank String firstName,
        @NotBlank String lastName
    ){}

    @Builder
    record CreditCard(
        @NotNull BigDecimal availableBalance,
        Map<@NotBlank String, @Positive BigDecimal> spendingByCategory
    ){}

    @Builder
    record Loan(
       @Positive BigDecimal initialAmount,
       @DecimalMin("0.00") BigDecimal currentAmount,
       @NotEmpty List<@Valid Person> guarantors
    ){}

    @Builder
    record Account(
        @NotNull @NotEmpty List<@Valid CreditCard> creditCards,
        @NotNull List<@Valid Loan> loans,
        @NotNull Map<AccessType, @NotEmpty List<@Valid Person>> access
    ){}

    enum AccessType {
        OWNER, FULL, READ
    }

    record PersonRenamed(
        @JsonProperty("fn") @NotBlank String firstName,
        @JsonProperty("ln") @NotBlank String lastName
    ){}

    @Builder
    record CreditCardRenamed(
        @JsonProperty("ab") @NotNull BigDecimal availableBalance,
        @JsonProperty("sbc") Map<@NotBlank String, @Positive BigDecimal> spendingByCategory
    ){}

    @Builder
    record LoanRenamed(
        @JsonProperty("ia") @Positive BigDecimal initialAmount,
        @JsonProperty("ca") @DecimalMin("0.00") BigDecimal currentAmount,
        @JsonProperty("g") @NotEmpty List<@Valid PersonRenamed> guarantors
    ){}

    @Builder
    record AccountRenamed(
        @JsonProperty("cc") @NotNull @NotEmpty List<@Valid CreditCardRenamed> creditCards,
        @JsonProperty("l") @NotNull List<@Valid LoanRenamed> loans,
        @JsonProperty("a") @NotNull Map<AccessType, @NotEmpty List<@Valid PersonRenamed>> access
    ){}


}