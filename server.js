import express from "express";
import path from "path";
import { fileURLToPath } from "url";
import { GoogleGenAI } from "@google/genai";
import dotenv from "dotenv";

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = 3000;

app.use(express.json());

// Initialize Gemini SDK
const apiKey = process.env.GEMINI_API_KEY;
const ai = apiKey
  ? new GoogleGenAI({
      apiKey,
      httpOptions: {
        headers: {
          "User-Agent": "aistudio-build",
        },
      },
    })
  : null;

// Endpoint to generate a customized workout
app.post("/api/workout/generate", async (req, res) => {
  try {
    const { prompt, durationMinutes } = req.body;
    const userPrompt = prompt || "general full body workout";
    const minutes = parseInt(durationMinutes) || 10;

    if (!ai) {
      return res.status(500).json({
        error: "Gemini API key is missing. Please configure GEMINI_API_KEY environment variable on Render.",
      });
    }

    console.log(`Generating workout for prompt: "${userPrompt}" with duration: ${minutes}m`);

    const systemInstruction = `You are an elite personal fitness trainer who specializes in coaching blind and visually impaired people.
Your task is to design a safe, effective, and fully voice-guided workout based on the user's request.
Since the user cannot see the screen or video demonstrations, your exercise descriptions MUST be extremely descriptive, step-by-step, explaining body positioning, balance checkpoints, and physical coordinates (e.g. "raise your arms until they are level with your shoulders", "stand with your feet slightly wider than your shoulders").
Always prioritize safety, joint care, and clear spatial awareness guidance. Do not use highly complex visual analogies.
Keep the workout within the requested total duration of ${minutes} minutes. Each exercise should ideally be between 30 to 60 seconds. Add rest breaks of 10 to 30 seconds between exercises.
Return your response strictly in the specified JSON schema format.`;

    const workoutPrompt = `Generate a customized ${minutes}-minute workout routine for this request: "${userPrompt}". Include an introductory warm-up and a final cool-down. Ensure every exercise verbal instruction is highly detailed so a blind person can follow it perfectly.`;

    const response = await ai.models.generateContent({
      model: "gemini-3.5-flash",
      contents: workoutPrompt,
      config: {
        systemInstruction,
        responseMimeType: "application/json",
        responseSchema: {
          type: "OBJECT",
          properties: {
            title: {
              type: "STRING",
              description: "Warm, encouraging title of the workout (e.g. 'Gentle Morning Energy Stretch')",
            },
            description: {
              type: "STRING",
              description: "A summary of the workout, what benefits it brings, and a brief safety advice.",
            },
            totalDurationSeconds: {
              type: "INTEGER",
              description: "Sum of all exercise durations and rest times in seconds.",
            },
            exercises: {
              type: "ARRAY",
              items: {
                type: "OBJECT",
                properties: {
                  id: { type: "STRING" },
                  name: { type: "STRING", description: "Name of the exercise" },
                  durationSeconds: { type: "INTEGER", description: "Active exercise duration in seconds" },
                  restDurationSeconds: { type: "INTEGER", description: "Rest time after this exercise in seconds" },
                  verbalInstruction: {
                    type: "STRING",
                    description: "Extremely descriptive step-by-step audio-guide script detailing foot placement, hand motion, form checkpoints, and posture for a blind person to follow correctly.",
                  },
                  safetyTip: {
                    type: "STRING",
                    description: "Specific form caution or modification to prevent injury (e.g. 'keep your knees behind your toes during squats').",
                  },
                },
                required: ["id", "name", "durationSeconds", "restDurationSeconds", "verbalInstruction"],
              },
            },
          },
          required: ["title", "description", "totalDurationSeconds", "exercises"],
        },
      },
    });

    const responseText = response.text;
    if (!responseText) {
      throw new Error("Empty response received from Gemini API");
    }

    const workoutData = JSON.parse(responseText.trim());
    res.json(workoutData);
  } catch (error) {
    console.error("Workout generation failed:", error);
    res.status(500).json({
      error: "Failed to generate workout. " + (error.message || ""),
    });
  }
});

// Serve static files (like index.html) directly from root
app.use(express.static(path.join(__dirname)));

app.get("*", (req, res) => {
  res.sendFile(path.join(__dirname, "index.html"));
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`[Server] Running on http://0.0.0.0:${PORT}`);
});
