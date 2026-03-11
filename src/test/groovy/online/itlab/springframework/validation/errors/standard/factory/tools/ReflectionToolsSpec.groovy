package online.itlab.springframework.validation.errors.standard.factory.tools

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.KnownImmutable
import lombok.Builder
import spock.lang.Specification

/**
 * Unit tests for {@link ReflectionTools}.
 */
class ReflectionToolsSpec extends Specification {

    ReflectionTools testedTools

    def setup() {
        testedTools = new ReflectionTools()
    }

    def 'correctly translates java path into json path'() {
        when:
            var jsonPath = testedTools.toJsonPath(clazz, javaPath)
        then:
            jsonPath == expectedJsonPath
        where:
            javaPath                           | clazz           || expectedJsonPath
            'person.firstName'                 | Employee        || javaPath
            'position'                         | Employee        || javaPath
            'person.firstName'                 | EmployeeRenamed || 'pe.fn'
            'position'                         | EmployeeRenamed || 'po'
            // collections part
            'loans[1].guarantors[3].firstName' | Finances        || 'loans[1].guarantors[3].firstName'
            'loans[1].guarantors[3].firstName' | FinancesRenamed || 'l[1].g[3].fn'
    }

    def 'throws exception when there is no java path'() {
        when:
            testedTools.toJsonPath(clazz, javaPath)
        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == expectedExceptionMessage
        where:
            javaPath           | clazz           || expectedExceptionMessage
            'person.xxx'       | Employee        || "Field 'xxx' not found in ${Person.name}"
            'aaa.bbb'          | Employee        || "Field 'aaa' not found in ${Employee.name}"
    }

    def 'correctly retrieves Field from record and class'() {
        when:
            var field = testedTools.findField(PersonRenamed, fieldName)
        then:
            field != null
            field.getName() == fieldName
        where:
            fieldName   | clazz
            'firstName' | Person
            'lastName'  | Person
            'height'    | Person
            'firstName' | PersonClass
            'lastName'  | PersonClass
            'height'    | PersonClass
    }

    @KnownImmutable
    record Person(
        String firstName,
        String lastName,
        Integer height
    ){}

    record Employee(
        Person person,
        String position
    ){}

    @KnownImmutable
    record PersonRenamed(
        @JsonProperty("fn") String firstName,
        @JsonProperty("ln") String lastName,
        @JsonProperty("h") Integer height
    ){}

    record EmployeeRenamed(
        @JsonProperty("pe") PersonRenamed person,
        @JsonProperty("po") String position
    ){}

    class PersonClass {
        String firstName;
        String lastName;
        Integer height;
    }


    record CreditCard(
        BigDecimal availableBalance,
        Map<String, BigDecimal> spendingByCategory
    ){}

    record Loan(
        BigDecimal initialAmount,
        BigDecimal currentAmount,
        List<Person> guarantors
    ){}

    record Finances(
        List<CreditCard> creditCards,
        List<Loan> loans
    ){}

    @Builder
    record CreditCardRenamed(
        @JsonProperty("ab") BigDecimal availableBalance,
        @JsonProperty("sbc") Map<String, BigDecimal> spendingByCategory
    ){}

    @Builder
    record LoanRenamed(
        @JsonProperty("ia") BigDecimal initialAmount,
        @JsonProperty("ca") BigDecimal currentAmount,
        @JsonProperty("g") List<PersonRenamed> guarantors
    ){}

    @Builder
    record FinancesRenamed(
        @JsonProperty("cc") List<CreditCardRenamed> creditCards,
        @JsonProperty("l") List<LoanRenamed> loans
    ){}
}
