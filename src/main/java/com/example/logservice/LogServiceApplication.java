package com.example.logservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LogServiceApplication {
    public static void main(String[] args) {
        System.out.println("LogServiceApplication");
        SpringApplication.run(LogServiceApplication.class, args);
    }
}