package com.example.voicefitness.controller;

import com.example.voicefitness.dto.WorkoutRequest;
import com.example.voicefitness.service.WorkoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class WorkoutController {

    private static final Logger logger = LoggerFactory.getLogger(WorkoutController.class);

    @Autowired
    private WorkoutService workoutService;

    @PostMapping(value = "/api/workout/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> generateWorkout(@RequestBody WorkoutRequest request) {
        try {
            logger.info("Received request to generate workout. Prompt: '{}', Duration: {} min",
                    request.getPrompt(), request.getDurationMinutes());

            String result = workoutService.generateWorkout(request.getPrompt(), request.getDurationMinutes());
            return ResponseEntity.ok(result);

        } catch (IllegalStateException e) {
            logger.error("Configuration error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");

        } catch (Exception e) {
            logger.error("Error occurred while generating workout: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Failed to generate workout. " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
