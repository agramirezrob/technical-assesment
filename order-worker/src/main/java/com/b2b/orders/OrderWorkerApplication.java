package com.b2b.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrderWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderWorkerApplication.class, args);
    }
}
