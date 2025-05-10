package io.bokun.inventory.plugin.tourcms.api;

import io.bokun.inventory.plugin.tourcms.util.AppLogger;
import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class BokunClient {
    private static final String TAG = "BokunClient";
    private static final String BASE_URL = "https://api.bokun.io";
    private final OkHttpClient client;
    private final String accessKey;
    private final String secretKey;

    public BokunClient() {
        this.client = new OkHttpClient();
        this.accessKey = System.getenv("BOKUN_API_KEY");
        this.secretKey = System.getenv("BOKUN_SECRET");

        AppLogger.info(TAG, "BokunClient Initialized");
    }

    private String generateSignature(String method, String endpoint, String date) {
        try {
            String message = date + accessKey + method + endpoint;
            AppLogger.info(TAG, "Message to sign: " + message);

            SecretKeySpec signingKey = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1");

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));

            String signature = Base64.getEncoder().encodeToString(rawHmac);
            AppLogger.info(TAG, "Generated Signature: " + signature);
            return signature;
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to generate signature", e);
            return null;
        }
    }

    private String buildRequest(String endpoint, String method, RequestBody body) throws IOException {
        String fullUrl = BASE_URL + endpoint;
        AppLogger.info(TAG, String.format("Calling API: %s %s", method, fullUrl));

        String date = Instant.now().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String signature = generateSignature(method, endpoint, date);
        if (signature == null) {
            throw new IOException("Failed to generate signature");
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(fullUrl)
                .addHeader("X-Bokun-Date", date)
                .addHeader("X-Bokun-AccessKey", accessKey)
                .addHeader("X-Bokun-Signature", signature)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json");

        AppLogger.info(TAG, "X-Bokun-Date: " + date);
        AppLogger.info(TAG, "X-Bokun-AccessKey: " + accessKey);
        AppLogger.info(TAG, "X-Bokun-Signature: " + signature);

        if (body != null) {
            AppLogger.info(TAG, "Request Body: " + body.toString());
        }

        switch (method.toUpperCase()) {
            case "POST":
                requestBuilder.post(body);
                break;
            case "PUT":
                requestBuilder.put(body);
                break;
            case "DELETE":
                requestBuilder.delete(body);
                break;
            default:
                requestBuilder.get();
                break;
        }

        Request request = requestBuilder.build();
        try (Response response = client.newCall(request).execute()) {
            AppLogger.info(TAG, "Response Code: " + response.code());

            // 🔹 Chỉ gọi response.body().string() duy nhất một lần
            String responseBody = response.body().string();
            AppLogger.info(TAG, "Response Body: " + responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("Failed to call API: " + response.message());
            }
            return responseBody;
        }
    }

    public String searchActivities(String queryJson) throws IOException {
        RequestBody body = RequestBody.create(queryJson, MediaType.parse("application/json"));
        return buildRequest("/activity.json/search", "POST", body);
    }

    public String getAvailability(String productId, String date) throws IOException {
        String endpoint = String.format("/activity.json/%s/availabilities?start=%s&end=%s", productId, date, date);
        return buildRequest(endpoint, "GET", null);
    }

    public String createBooking(String bookingJson) throws IOException {
        RequestBody body = RequestBody.create(bookingJson, MediaType.parse("application/json"));
        return buildRequest("/activity.json/booking", "POST", body);
    }

    public String getBookings() throws IOException {
        return buildRequest("/activity.json/booking", "GET", null);
    }

    public String getProducts() throws IOException {
        return buildRequest("/activity.json/products", "GET", null);
    }

    public String getProductSlots(String productId) throws IOException {
        return buildRequest("/activity.json/" + productId + "/slots", "GET", null);
    }

    public boolean checkAvailability(String productId, String date) throws IOException {
        String response = getAvailability(productId, date);
        return response.contains("\"available\":true");
    }

    public boolean checkBookingExists(String bookingId) throws IOException {
        String response = buildRequest("/activity.json/booking/" + bookingId, "GET", null);
        return response.contains("\"id\":");
    }


    public static void main(String[] args) {
        BokunClient bokunClient = new BokunClient();
        String queryJson = "{\n" +
                "  \"query\": {\n" +
                "    \"text\": \"\",\n" +
                "    \"start\": \"2025-06-01\",\n" +
                "    \"end\": \"2025-06-30\",\n" +
                "    \"numAdults\": 1\n" +
                "  }\n" +
                "}";

        try {
            bokunClient.searchActivities(queryJson);
        } catch (IOException e) {
            AppLogger.error("Main", "Error while searching activities", e);
        }
    }
}
