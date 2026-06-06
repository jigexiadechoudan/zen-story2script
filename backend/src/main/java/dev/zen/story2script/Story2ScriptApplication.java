package dev.zen.story2script;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Story2ScriptApplication {

    public static void main(String[] args) {
        SpringApplication.run(Story2ScriptApplication.class, args);
    }
}
