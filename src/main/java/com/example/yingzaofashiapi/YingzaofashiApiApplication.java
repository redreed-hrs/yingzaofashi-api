package com.example.yingzaofashiapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class YingzaofashiApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(YingzaofashiApiApplication.class, args);
    }

}