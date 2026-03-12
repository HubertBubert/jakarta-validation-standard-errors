package online.itlab.springframework.validation.errors.standard.factory.domain

import spock.lang.Specification

/**
 * Unit tests for {@link ValidationPathFactory}.
 */
class ValidationPathFactorySpec extends Specification {

    ValidationPathFactory testedFactory

    def setup() {
        testedFactory = new ValidationPathFactory()
    }

    def 'correctly splits into java path and indexes'() {
        when:
            var result = testedFactory.create(javaValidationPath)
        then:
            result.javaFieldName() == expectedJavaFieldName
            result.indexPart() == expectedIndexPart
        where:
            javaValidationPath || expectedJavaFieldName || expectedIndexPart
            'name'             || 'name'                || ''
            'names[]'          || 'names'               || '[]'
            'names[1]'         || 'names'               || '[1]'
            'people[1][]'      || 'people'              || '[1][]'
    }
}
