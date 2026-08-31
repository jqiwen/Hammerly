package com.hammerly.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class HammerlyAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(HammerlyAiApplication.class, args);
    }
}
