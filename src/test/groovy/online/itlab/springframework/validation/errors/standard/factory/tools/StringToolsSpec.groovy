package online.itlab.springframework.validation.errors.standard.factory.tools

import spock.lang.Specification

/**
 * Unit tests for {@link StringTools}.
 */
class StringToolsSpec extends Specification {

    StringTools testedTools

    def setup() {
        testedTools = new StringTools()
    }

    def 'correctly translates java path into json path'() {
        when:
        var lastSegment = testedTools.lastSegment(string, (char) delimiter)
        then:
            lastSegment == expectedLastSegment
        where:
            string        | delimiter || expectedLastSegment
            'aaa.bbb.ccc' | '.'       || 'ccc'
            'aaa.bbb.ccc' | ';'       || string
            'aaa;bbb;ccc' | ';'       || 'ccc'
            'aaabbbccc'   | '.'       || string
            '   '         | '.'       || string
            null          | '.'       || string
    }
}
