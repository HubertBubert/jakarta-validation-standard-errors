package online.itlab.springframework.validation.errors.standard.factory.domain;

public interface IValidationPathFactory {
    ValidationPath create(final String validationJavaPath);

    record ValidationPath(
        String javaFieldName,
        String indexPart
    ){}
}
