package online.itlab.springframework.validation.errors.standard.factory.domain.types;

public enum In {
    PATH("path"),
    QUERY("query"),
    MATRIX("matrix"),
    HEADER("header"),
    COOKIE("cookie"),
    BODY("body"),
    // problematic types to be investigated
    MULTI("multi"),         // no tests
    PART("part"),           // investigate implementation PART vs MULTI
    DUMMY("dummy"),         // replace proper logic - no tests
    UNKNOWN("unknown"),         // probably used incorrectly - investigate usage logic - no tests
    PARAMETER("parameter");     // used but this logic has not been tested - write tests and evaluate value

    public final String value;

    In(final String value){
        this.value = value;
    }
}
