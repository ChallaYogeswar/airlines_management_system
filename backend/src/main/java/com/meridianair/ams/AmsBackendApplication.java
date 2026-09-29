package com.meridianair.ams;

import com.meridianair.ams.opensky.OpenSkyProperties;
import com.meridianair.ams.security.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({OpenSkyProperties.class, SecurityProperties.class})
public class AmsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AmsBackendApplication.class, args);
    }
}
