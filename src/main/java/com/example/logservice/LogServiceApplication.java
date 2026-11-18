package com.example.logservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LogServiceApplication {
    public static void main(String[] args) {
        System.out.println("LogServiceApplication");
        System.out.println("得不到的都释怀");
        SpringApplication.run(LogServiceApplication.class, args);
    }
}
