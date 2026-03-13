package online.itlab.springframework.validation.errors.standard.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jvse")
public class JvseConfiguration {
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
