package co.zeal.wellbeing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WellbeingApplication {

    public static void main(String[] args) {
        SpringApplication.run(WellbeingApplication.class, args);
    }
}
