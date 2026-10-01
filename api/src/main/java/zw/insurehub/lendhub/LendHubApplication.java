package zw.insurehub.lendhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** LendHub — microfinance loan management for InsureHub Microfinance (fictional). */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
public class LendHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(LendHubApplication.class, args);
    }
}
