package online.itlab.springframework.validation.errors.standard.autoconfigure;

import online.itlab.springframework.validation.errors.standard.configuration.JvseConfiguration;
import online.itlab.springframework.validation.errors.standard.factory.IJakartaValidationProblemDetailFactory;
import online.itlab.springframework.validation.errors.standard.factory.JakartaValidationProblemDetailFactory;
import online.itlab.springframework.validation.errors.standard.factory.domain.IValidationPathFactory;
import online.itlab.springframework.validation.errors.standard.factory.domain.ValidationPathFactory;
import online.itlab.springframework.validation.errors.standard.factory.tools.ReflectionTools;
import online.itlab.springframework.validation.errors.standard.factory.tools.StringTools;
import online.itlab.springframework.validation.errors.standard.factory.tools.WebRequestTools;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(JvseConfiguration.class)
public class LibAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public IJakartaValidationProblemDetailFactory problemDetailFactory() {
        final IValidationPathFactory validationPathFactory = new ValidationPathFactory();
        return new JakartaValidationProblemDetailFactory(
            new ReflectionTools(validationPathFactory),
            new StringTools(),
            new WebRequestTools(),
            validationPathFactory
        );
    }
}
