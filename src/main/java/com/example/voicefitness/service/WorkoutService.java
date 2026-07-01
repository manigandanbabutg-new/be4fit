package com.example.voicefitness.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class WorkoutService {

    private static final Logger logger = LoggerFactory.getLogger(WorkoutService.class);

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public String generateWorkout(String userPrompt, int durationMinutes) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured on the server side.");
        }

        String actualPrompt = userPrompt != null && !userPrompt.trim().isEmpty() ? userPrompt : "general full body workout";
        int minutes = durationMinutes > 0 ? durationMinutes : 10;

        logger.info("Requesting customized workout from Gemini API for prompt: \"{}\", duration: {} minutes", actualPrompt, minutes);

        String systemInstructionText = "You are an elite personal fitness trainer who specializes in coaching blind and visually impaired people. " +
                "Your task is to design a safe, effective, and fully voice-guided workout based on the user's request. " +
                "Since the user cannot see the screen or video demonstrations, your exercise descriptions MUST be extremely descriptive, step-by-step, explaining body positioning, balance checkpoints, and physical coordinates (e.g. 'raise your arms until they are level with your shoulders', 'stand with your feet slightly wider than your shoulders'). " +
                "Always prioritize safety, joint care, and clear spatial awareness guidance. Do not use highly complex visual analogies. " +
                "Keep the workout within the requested total duration of " + minutes + " minutes. Each exercise should ideally be between 30 to 60 seconds. Add rest breaks of 10 to 30 seconds between exercises. " +
                "Return your response strictly in the specified JSON schema format.";

        String userPromptText = "Generate a customized " + minutes + "-minute workout routine for this request: \"" + actualPrompt + "\". Include an introductory warm-up and a final cool-down. Ensure every exercise verbal instruction is highly detailed so a blind person can follow it perfectly.";

        // Construct the JSON payload using Java 17 Text Blocks
        String requestJson = """
        {
          "contents": [
            {
              "parts": [
                {
                  "text": "%s"
                }
              ]
            }
          ],
          "systemInstruction": {
            "parts": [
              {
                "text": "%s"
              }
            ]
          },
          "generationConfig": {
            "responseMimeType": "application/json",
            "responseSchema": {
              "type": "OBJECT",
              "properties": {
                "title": {
                  "type": "STRING",
                  "description": "Warm, encouraging title of the workout (e.g. 'Gentle Morning Energy Stretch')"
                },
                "description": {
                  "type": "STRING",
                  "description": "A summary of the workout, what benefits it brings, and a brief safety advice."
                },
                "totalDurationSeconds": {
                  "type": "INTEGER",
                  "description": "Sum of all exercise durations and rest times in seconds."
                },
                "exercises": {
                  "type": "ARRAY",
                  "items": {
                    "type": "OBJECT",
                    "properties": {
                      "id": { "type": "STRING" },
                      "name": { "type": "STRING", "description": "Name of the exercise" },
                      "durationSeconds": { "type": "INTEGER", "description": "Active exercise duration in seconds" },
                      "restDurationSeconds": { "type": "INTEGER", "description": "Rest time after this exercise in seconds" },
                      "verbalInstruction": {
                        "type": "STRING",
                        "description": "Extremely descriptive step-by-step audio-guide script detailing foot placement, hand motion, form checkpoints, and posture for a blind person to follow correctly."
                      },
                      "safetyTip": {
                        "type": "STRING",
                        "description": "Specific form caution or modification to prevent injury (e.g. 'keep your knees behind your toes during squats')."
                      }
                    },
                    "required": ["id", "name", "durationSeconds", "restDurationSeconds", "verbalInstruction"]
                  }
                }
              },
              "required": ["title", "description", "totalDurationSeconds", "exercises"]
            }
          }
        }
        """.formatted(escapeJson(userPromptText), escapeJson(systemInstructionText));

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("User-Agent", "aistudio-build")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        logger.debug("Sending request to Gemini API endpoint...");
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.error("Gemini API call failed. Status: {}, Body: {}", response.statusCode(), response.body());
            throw new IOException("Gemini API error. HTTP Status Code: " + response.statusCode());
        }

        // Parse response body to extract the text block
        String responseBody = response.body();
        return extractResponseJson(responseBody);
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\b", "\\b")
                   .replace("\f", "\\f")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private String extractResponseJson(String responseBody) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(responseBody);
            com.fasterxml.jackson.databind.JsonNode textNode = rootNode
                    .path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text");
            if (!textNode.isMissingNode()) {
                return textNode.asText();
            }
        } catch (Exception e) {
            logger.error("Jackson failed to parse Gemini response: {}", e.getMessage());
        }
        return responseBody;
    }
}
