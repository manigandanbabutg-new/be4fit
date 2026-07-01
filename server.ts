import express from "express";
import path from "path";
import { fileURLToPath } from "url";
import { createServer as createViteServer } from "vite";
import { GoogleGenAI, Type } from "@google/genai";
import dotenv from "dotenv";

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json());

  // Initialize Gemini SDK with telemetry header
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
          error: "Gemini API key is missing. Please set GEMINI_API_KEY in the Secrets panel.",
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
            type: Type.OBJECT,
            properties: {
              title: {
                type: Type.STRING,
                description: "Warm, encouraging title of the workout (e.g. 'Gentle Morning Energy Stretch')",
              },
              description: {
                type: Type.STRING,
                description: "A summary of the workout, what benefits it brings, and a brief safety advice.",
              },
              totalDurationSeconds: {
                type: Type.INTEGER,
                description: "Sum of all exercise durations and rest times in seconds.",
              },
              exercises: {
                type: Type.ARRAY,
                items: {
                  type: Type.OBJECT,
                  properties: {
                    id: { type: Type.STRING },
                    name: { type: Type.STRING, description: "Name of the exercise" },
                    durationSeconds: { type: Type.INTEGER, description: "Active exercise duration in seconds" },
                    restDurationSeconds: { type: Type.INTEGER, description: "Rest time after this exercise in seconds" },
                    verbalInstruction: {
                      type: Type.STRING,
                      description: "Extremely descriptive step-by-step audio-guide script detailing foot placement, hand motion, form checkpoints, and posture for a blind person to follow correctly.",
                    },
                    safetyTip: {
                      type: Type.STRING,
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
    } catch (error: any) {
      console.error("Workout generation failed:", error);
      res.status(500).json({
        error: "Failed to generate workout. " + (error.message || ""),
      });
    }
  });

  // Integrate Vite middleware for dev mode or serve static files in production
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), "dist");
    app.use(express.static(distPath));
    app.get("*", (req, res) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`[Server] Running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
