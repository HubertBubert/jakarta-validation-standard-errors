package online.itlab.springframework.validation.errors.standard.factory.tools

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.KnownImmutable
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
            javaPath                                       | clazz           || expectedJsonPath
            'person.firstName'                             | Employee        || javaPath
            'position'                                     | Employee        || javaPath
            'person.firstName'                             | EmployeeRenamed || 'pe.fn'
            'position'                                     | EmployeeRenamed || 'po'
            // collections part
            'loans[1].guarantors[3].firstName'             | Finances        || 'loans[1].guarantors[3].firstName'
            'loans[1].guarantors[3].firstName'             | FinancesRenamed || 'l[1].g[3].fn'
            'creditCards[4].spendingByCategory[food]'      | Finances        || 'creditCards[4].spendingByCategory[food]'
            'creditCards[4].spendingByCategory[food]'      | FinancesRenamed || 'cc[4].sbc[food]'
            // maps part
            'subAccounts[main].beneficiaries[1].firstName' | Finances        || 'subAccounts[main].beneficiaries[1].firstName'
            'subAccounts[main].beneficiaries[1].firstName' | FinancesRenamed || 'sa[main].be[1].fn'
            'subAccounts[main].balances[usd]'              | Finances        || 'subAccounts[main].balances[usd]'
            'subAccounts[main].balances[usd]'              | FinancesRenamed || 'sa[main].ba[usd]'

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
        Map<String, BigDecimal> spendingByCategory
    ){}

    record Loan(
        List<Person> guarantors
    ){}

    record SubAccount(
       List<Person> beneficiaries,
       Map<String, BigDecimal> balances
    ){}

    record Finances(
        List<CreditCard> creditCards,               // list of maps
        List<Loan> loans,                           // list of lists
        Map<String, SubAccount> subAccounts         // map of lists + map of maps
    ){}

    record CreditCardRenamed(
        @JsonProperty("sbc") Map<String, BigDecimal> spendingByCategory
    ){}

    record LoanRenamed(
        @JsonProperty("g") List<PersonRenamed> guarantors
    ){}

    record SubAccountRenamed(
        @JsonProperty("be")  List<PersonRenamed> beneficiaries,
        @JsonProperty("ba") Map<String, BigDecimal> balances
    ){}

    record FinancesRenamed(
        @JsonProperty("cc") List<CreditCardRenamed> creditCards,            // list of maps
        @JsonProperty("l") List<LoanRenamed> loans,                         // list of lists
        @JsonProperty("sa") Map<String, SubAccountRenamed> subAccounts      // map of lists + map of maps
    ){}
}
