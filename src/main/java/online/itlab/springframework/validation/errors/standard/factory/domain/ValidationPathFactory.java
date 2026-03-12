package online.itlab.springframework.validation.errors.standard.factory.domain;

public class ValidationPathFactory implements IValidationPathFactory {
    /**
     * Separates java field name from index part which is available for collections and maps.
     * @param validationJavaPath Validation path created by Jakarta Validation.
     *                           eg: name, names[1], employees[ceo]
     * @return Separated java field path and index part.
     */
    public ValidationPath create(final String validationJavaPath) {
        int idx = validationJavaPath.indexOf('[');
        if (idx == -1) {
            return new ValidationPath(validationJavaPath, "");
        }
        return new ValidationPath(
            validationJavaPath.substring(0, idx),
            validationJavaPath.substring(idx)
        );
    }
}
