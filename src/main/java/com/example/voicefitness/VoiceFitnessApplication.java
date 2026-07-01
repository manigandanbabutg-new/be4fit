package com.example.voicefitness;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Entry Point for the Spring Boot application on Render.
 * Bootstraps the backend for the Voice Generated & Guided Fitness App.
 */
@SpringBootApplication
public class VoiceFitnessApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoiceFitnessApplication.class, args);
        System.out.println("=================================================");
        System.out.println("  Voice Fitness Spring Boot Backend is Online!   ");
        System.out.println("=================================================");
    }
}
