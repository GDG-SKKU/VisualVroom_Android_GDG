package edu.skku.cs.visualvroomandroid;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Client for interacting with Google's Gemini API to generate images from text
 */
public class GeminiApiClient {
    private static final String TAG = "GeminiApiClient";
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro-vision:generateContent";

    private final String apiKey;
    private final OkHttpClient client;

    public interface ImageGenerationCallback {
        void onSuccess(Bitmap bitmap);
        void onError(String errorMessage);
    }

    public GeminiApiClient(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Generate an image based on the provided text prompt
     *
     * @param prompt The text prompt to generate an image from
     * @param callback Callback to handle the result
     */
    public void generateImage(String prompt, ImageGenerationCallback callback) {
        try {
            // Prepare JSON request for Gemini
            JSONObject requestJson = buildRequestJson(prompt);

            // Create the request
            Request request = new Request.Builder()
                    .url(API_URL + "?key=" + apiKey)
                    .post(RequestBody.create(
                            MediaType.parse("application/json"), requestJson.toString()))
                    .build();

            // Send request
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to send request to Gemini: " + e.getMessage());
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful() || responseBody == null) {
                            Log.e(TAG, "Gemini API error: " + response.code());
                            callback.onError("API error: " + response.code());
                            return;
                        }

                        String jsonResponse = responseBody.string();
                        Log.d(TAG, "Gemini response received");

                        // Parse the response to get image data
                        Bitmap bitmap = extractImageFromResponse(jsonResponse);
                        if (bitmap != null) {
                            callback.onSuccess(bitmap);
                        } else {
                            callback.onError("No image found in response");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing Gemini response: " + e.getMessage());
                        callback.onError("Error processing response: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error creating Gemini request: " + e.getMessage());
            callback.onError("Error creating request: " + e.getMessage());
        }
    }

    /**
     * Build the JSON request for the Gemini API
     */
    private JSONObject buildRequestJson(String prompt) throws JSONException {
        JSONObject requestJson = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();

        // Add text prompt part
        JSONObject textPart = new JSONObject();
        textPart.put("text", "Generate an image based on this description: " + prompt);
        parts.put(textPart);

        content.put("parts", parts);
        contents.put(content);
        requestJson.put("contents", contents);

        // Configure generation parameters
        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 0.8);
        generationConfig.put("maxOutputTokens", 2048);
        requestJson.put("generationConfig", generationConfig);

        return requestJson;
    }

    /**
     * Extract image data from the Gemini API JSON response
     */
    private Bitmap extractImageFromResponse(String jsonResponse) throws JSONException {
        JSONObject jsonObject = new JSONObject(jsonResponse);

        if (!jsonObject.has("candidates")) {
            Log.e(TAG, "No candidates in response: " + jsonResponse);
            return null;
        }

        JSONArray candidates = jsonObject.getJSONArray("candidates");

        if (candidates.length() == 0) {
            Log.e(TAG, "Empty candidates array");
            return null;
        }

        JSONObject candidate = candidates.getJSONObject(0);
        JSONObject content = candidate.getJSONObject("content");
        JSONArray parts = content.getJSONArray("parts");

        // Find the part with the image
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.getJSONObject(i);
            if (part.has("inlineData")) {
                JSONObject inlineData = part.getJSONObject("inlineData");
                String mimeType = inlineData.getString("mimeType");
                String base64Data = inlineData.getString("data");

                // Convert base64 to bitmap
                if (mimeType.startsWith("image/")) {
                    byte[] decodedString = Base64.decode(base64Data, Base64.DEFAULT);
                    return BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                }
            }
        }

        return null;
    }
}