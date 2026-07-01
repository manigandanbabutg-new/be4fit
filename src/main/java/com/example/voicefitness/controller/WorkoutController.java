package com.example.voicefitness.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Spring Boot Controller implementing the Voice Generated & Guided Fitness Workout generation.
 * Replicates the precise prompt engineering, blind-user guidelines, and structured JSON output
 * matching the frontend application.
 */
@RestController
@RequestMapping("/api/workout")
@CrossOrigin(origins = "*")
public class WorkoutController {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/generate")
    public ResponseEntity<?> generateWorkout(@RequestBody Map<String, Object> payload) {
        try {
            String prompt = (String) payload.getOrDefault("prompt", "general full body workout");
            Integer durationMinutes = (Integer) payload.getOrDefault("durationMinutes", 10);

            if (geminiApiKey == null || geminiApiKey.trim().isEmpty() || "MY_GEMINI_API_KEY".equals(geminiApiKey)) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Gemini API key is missing on the Java backend configuration. Please configure gemini.api.key."));
            }

            // Endpoint for Gemini API (using 3.5-flash)
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + geminiApiKey;

            // Build system instructions optimized for coaching blind and visually impaired users
            String systemInstruction = "You are an elite personal fitness trainer who specializes in coaching blind and visually impaired people. " +
                    "Your task is to design a safe, effective, and fully voice-guided workout based on the user's request. " +
                    "Since the user cannot see the screen or video demonstrations, your exercise descriptions MUST be extremely descriptive, step-by-step, explaining body positioning, balance checkpoints, and physical coordinates (e.g. 'raise your arms until they are level with your shoulders', 'stand with your feet slightly wider than your shoulders'). " +
                    "Always prioritize safety, joint care, and clear spatial awareness guidance. Do not use highly complex visual analogies. " +
                    "Keep the workout within the requested total duration of " + durationMinutes + " minutes. Each exercise should ideally be between 30 to 60 seconds. Add rest breaks of 10 to 30 seconds between exercises. " +
                    "Return your response strictly in JSON format matching the schema structure.";

            String userPrompt = "Generate a customized " + durationMinutes + "-minute workout routine for this request: \"" + prompt + "\". Include an introductory warm-up and a final cool-down. Ensure every exercise verbal instruction is highly detailed so a blind person can follow it perfectly.";

            // Construct Gemini Request Body with Structured JSON output expectation
            ObjectNode requestBody = objectMapper.createObjectNode();
            
            // Contents
            ObjectNode contentsNode = requestBody.putObject("contents");
            ObjectNode partsNode = contentsNode.putArray("parts").addObject();
            partsNode.put("text", userPrompt);

            // System Instruction
            ObjectNode sysInstructionNode = requestBody.putObject("systemInstruction");
            ObjectNode sysPartsNode = sysInstructionNode.putArray("parts").addObject();
            sysPartsNode.put("text", systemInstruction);

            // Generation Config
            ObjectNode generationConfig = requestBody.putObject("generationConfig");
            generationConfig.put("responseMimeType", "application/json");

            // Define JSON schema to match frontend TypeScript types (Workout, Exercise)
            ObjectNode responseSchema = generationConfig.putObject("responseSchema");
            responseSchema.put("type", "OBJECT");
            
            ObjectNode properties = responseSchema.putObject("properties");
            
            properties.putObject("title")
                    .put("type", "STRING")
                    .put("description", "Warm, encouraging title of the workout");
            
            properties.putObject("description")
                    .put("type", "STRING")
                    .put("description", "A summary of the workout, what benefits it brings, and brief safety advice");
            
            properties.putObject("totalDurationSeconds")
                    .put("type", "INTEGER");

            ObjectNode exercisesArray = properties.putObject("exercises");
            exercisesArray.put("type", "ARRAY");
            ObjectNode exerciseItems = exercisesArray.putObject("items");
            exerciseItems.put("type", "OBJECT");
            ObjectNode exerciseProps = exerciseItems.putObject("properties");
            
            exerciseProps.putObject("id").put("type", "STRING");
            exerciseProps.putObject("name").put("type", "STRING");
            exerciseProps.putObject("durationSeconds").put("type", "INTEGER");
            exerciseProps.putObject("restDurationSeconds").put("type", "INTEGER");
            exerciseProps.putObject("verbalInstruction").put("type", "STRING")
                    .put("description", "Extremely descriptive audio-guide script detailing foot placement, hand motion, form checkpoints, and posture for a blind person to follow.");
            exerciseProps.putObject("safetyTip").put("type", "STRING");

            // Build headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);

            // Make HTTP Post Request to Gemini Endpoint
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                // Parse Gemini Response to extract JSON string from candidate parts
                ObjectMapper mapper = new ObjectMapper();
                Map<?, ?> responseMap = mapper.readValue(response.getBody(), Map.class);
                
                // Navigate response JSON structure: candidates[0].content.parts[0].text
                java.util.List<?> candidates = (java.util.List<?>) responseMap.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
                    Map<?, ?> content = (Map<?, ?>) firstCandidate.get("content");
                    if (content != null) {
                        java.util.List<?> parts = (java.util.List<?>) content.get("parts");
                        if (parts != null && !parts.isEmpty()) {
                            Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
                            String rawJsonText = (String) firstPart.get("text");
                            
                            // Return the structured workout JSON directly
                            return ResponseEntity.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(rawJsonText);
                        }
                    }
                }
            }

            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Received unexpected response format from Google AI services."));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Java backend error generating workout: " + e.getMessage()));
        }
    }
}
