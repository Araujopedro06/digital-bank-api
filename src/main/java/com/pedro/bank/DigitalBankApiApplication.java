package com.pedro.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DigitalBankApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DigitalBankApiApplication.class, args);
    }
}
