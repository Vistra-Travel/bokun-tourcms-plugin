package io.bokun.inventory.plugin.tourcms.api;

import io.bokun.inventory.plugin.tourcms.util.AppLogger;
import okhttp3.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class WebhookClient {

    private static final String TAG = "WebhookSender";
    private static final String WEBHOOK_URL = "https://hook.eu1.make.com/wb7kjoyhpp1nvpa5dkhcqlhrub09h5es";
    private static final OkHttpClient httpClient = new OkHttpClient();

    public static CompletableFuture<Void> sendWebhook(Map<String, String> data) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Tạo JSON body
        String jsonBody = "{";
        for (Map.Entry<String, String> entry : data.entrySet()) {
            jsonBody += "\"" + entry.getKey() + "\": \"" + entry.getValue() + "\", ";
        }
        jsonBody = jsonBody.substring(0, jsonBody.length() - 2) + "}";

        // Cấu hình request
        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(WEBHOOK_URL)
                .post(body)
                .header("Content-Type", "application/json")
                .build();

        AppLogger.info(TAG, "Sending webhook with data: " + jsonBody);

        // Thực hiện gửi request
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                AppLogger.error(TAG, "❌ Failed to send webhook: " + e.getMessage(), e);
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    AppLogger.info(TAG, "✅ Webhook sent successfully!");
                    future.complete(null);
                } else {
                    String responseBody = response.body() != null ? response.body().string() : "No response body";
                    AppLogger.error(TAG, String.format("❌ Failed to send webhook: %s - %s", response.code(), responseBody));
                    future.completeExceptionally(new IOException(String.format("%d - %s", response.code(), responseBody)));
                }
                response.close();
            }
        });

        return future;
    }

    // Main để test
    public static void main(String[] args) {
        Map<String, String> data = new HashMap<>();
        data.put("platform", "BOKUN_TOURCMS_PLUGIN");
        data.put("booking_confirmation_code", "123456789");
        data.put("first_name", "John");
        data.put("last_name", "Doe");
        data.put("voucher_link", "https://example.com/voucher/12345");
        data.put("phone_number", "+84987654321");

        sendWebhook(data).whenComplete((result, error) -> {
            if (error != null) {
                AppLogger.error(TAG, "❌ Error sending webhook: " + error.getMessage(), error);
            } else {
                AppLogger.info(TAG, "✅ Webhook sent successfully!");
            }
        });

        try {
            // Để chương trình chạy cho tới khi gửi xong
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            AppLogger.error(TAG, "Thread interrupted", e);
        }
    }
}
