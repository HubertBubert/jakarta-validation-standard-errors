package online.itlab.springframework.validation.errors.standard.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import online.itlab.springframework.validation.errors.standard.configuration.JvseConfig;
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

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(JvseConfig.class)
public class JvseAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public IJakartaValidationProblemDetailFactory problemDetailFactory(final JvseConfig configuration) {
        final IValidationPathFactory validationPathFactory = new ValidationPathFactory();
        return new JakartaValidationProblemDetailFactory(
            configuration.getLabels(),
            configuration.getValues(),
            new ReflectionTools(validationPathFactory),
            new StringTools(),
            new WebRequestTools(),
            validationPathFactory
        );
    }
}
