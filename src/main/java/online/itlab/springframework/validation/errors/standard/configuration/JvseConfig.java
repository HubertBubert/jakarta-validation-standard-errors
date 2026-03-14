package online.itlab.springframework.validation.errors.standard.configuration;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.http.HttpStatus;

import java.net.URI;


@Getter
@Setter
@ToString
@ConfigurationProperties("jvse")
public class JvseConfig {

    private boolean enabled = true;

    @NestedConfigurationProperty
    private ValuesConfig values = new ValuesConfig();

    @NestedConfigurationProperty
    private LabelsConfig labels = new LabelsConfig();

    @Getter
    @Setter
    @ToString
    public static class ValuesConfig {
        @NestedConfigurationProperty
        private ValuesConfigType type = new ValuesConfigType();

        private String title = "Request Validation Failed";
        private String detail = "Request has one or more validation errors. Please fix them and try again.";
        private HttpStatus status = HttpStatus.BAD_REQUEST;
    }

    @Getter
    @Setter
    @ToString
    public static class LabelsConfig {
        private String in = "in";
        private String name = "name";
        private String path = "path";
        private String message = "message";
        private String rejectedValue = "rejectedValue";
    }

    @Setter
    @ToString
    public static class ValuesConfigType {
        private URI base = null;
        private URI path = URI.create("/problems/validation-failed");

        public URI getAbsolute() {
            return base == null
                ? path
                : base.resolve(path);
        }
    }

}
