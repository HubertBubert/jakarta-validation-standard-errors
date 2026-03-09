package online.itlab.springframework.validation.errors.standard.autoconfigure;

import online.itlab.springframework.validation.errors.standard.factory.IJakartaValidationProblemDetailFactory;
import online.itlab.springframework.validation.errors.standard.factory.JakartaValidationProblemDetailFactory;
import online.itlab.springframework.validation.errors.standard.factory.ReflectionTools;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class LibAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public IJakartaValidationProblemDetailFactory problemDetailFactory() {
        return new JakartaValidationProblemDetailFactory(new ReflectionTools());
    }
}
