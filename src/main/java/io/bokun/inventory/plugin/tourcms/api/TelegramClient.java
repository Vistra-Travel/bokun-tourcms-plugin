package io.bokun.inventory.plugin.tourcms.api;

import io.bokun.inventory.plugin.api.rest.ConfirmBookingRequest;
import io.bokun.inventory.plugin.tourcms.model.BookingCancelMessage;
import io.bokun.inventory.plugin.tourcms.model.BookingSuccessMessage;
import io.bokun.inventory.plugin.tourcms.model.TourCMSBooking;
import io.bokun.inventory.plugin.tourcms.util.AppLogger;
import okhttp3.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

public class TelegramClient {

    private static final String TAG = "TelegramClient";
    private static final String JSON_PATH = "src/main/resources/temps/booking_success.json";

    // Cấu hình lấy từ System.getenv hoặc giá trị mặc định
    private static final String TELEGRAM_BOT_TOKEN = System.getenv("TELEGRAM_BOT_TOKEN") != null
            ? System.getenv("TELEGRAM_BOT_TOKEN")
            : "8056450972:AAGf4IKe-Q-BB3tKWeVbq8qBXEDHZC1MjTU";

    private static final String TELEGRAM_GROUP_ID = System.getenv("TELEGRAM_GROUP_ID") != null
            ? System.getenv("TELEGRAM_GROUP_ID")
            : "-1002621180787";

    private static final int MAX_RETRIES = System.getenv("TELEGRAM_MAX_RETRIES") != null
            ? Integer.parseInt(System.getenv("TELEGRAM_MAX_RETRIES"))
            : 3;

    private static final int RETRY_DELAY = System.getenv("TELEGRAM_RETRY_DELAY") != null
            ? Integer.parseInt(System.getenv("TELEGRAM_RETRY_DELAY"))
            : 5000;

    // URL API của Telegram
    private static final String TELEGRAM_BASE_URL = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN + "/sendMessage";

    // HTTP Client dùng chung cho tất cả các phương thức static
    private static final OkHttpClient httpClient = new OkHttpClient();

    /**
     * Gửi tin nhắn tới Telegram với retry (Static Method)
     *
     * @param message Nội dung tin nhắn
     * @return CompletableFuture<Void>
     */
    public static CompletableFuture<Void> sendTelegramMessage(String message) {
        AppLogger.info(TAG, "Sending message to Telegram: " + message);
        return sendTelegramMessageWithRetry(message, MAX_RETRIES);
    }

    /**
     * Gửi tin nhắn với Retry Logic
     */
    private static CompletableFuture<Void> sendTelegramMessageWithRetry(String message, int retries) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        RequestBody body = new FormBody.Builder()
                .add("chat_id", TELEGRAM_GROUP_ID)
                .add("text", message)
                .add("parse_mode", "MarkdownV2")
                .build();

        Request request = new Request.Builder()
                .url(TELEGRAM_BASE_URL)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                AppLogger.error(TAG, String.format("Failed to send message. Retries left: %d", retries), e);
                if (retries > 0) {
                    try {
                        Thread.sleep(RETRY_DELAY);
                    } catch (InterruptedException ex) {
                        AppLogger.error(TAG, "Retry interrupted", ex);
                        future.completeExceptionally(ex);
                        return;
                    }
                    sendTelegramMessageWithRetry(message, retries - 1).whenComplete((result, error) -> {
                        if (error != null) {
                            AppLogger.error(TAG, "Failed after retries", error);
                            future.completeExceptionally(error);
                        } else {
                            AppLogger.info(TAG, "Message sent after retry");
                            future.complete(null);
                        }
                    });
                } else {
                    AppLogger.error(TAG, "All retries failed. Message not sent.");
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    AppLogger.info(TAG, "Message sent to Telegram successfully");
                    future.complete(null);
                } else {
                    AppLogger.error(TAG, String.format("Failed to send message: %s - %s", response.code(), response.message()));
                    future.completeExceptionally(new IOException("Error: " + response.code() + " - " + response.message()));
                }
                response.close();
            }
        });

        return future;
    }

    // Hàm load JSON từ file
    public static String loadJsonFromFile(String path) {
        try {
            byte[] encoded = Files.readAllBytes(Paths.get(path));
            return new String(encoded);
        } catch (IOException e) {
            AppLogger.error(TAG, "Error reading JSON file: " + path, e);
            return null;
        }
    }

    public static void main(String[] args) {
        // Test Booking Success Message
        /*String jsonResponse = loadJsonFromFile(JSON_PATH);
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            AppLogger.error(TAG, "Failed to load JSON from path: " + JSON_PATH);
            return;
        }

        BookingSuccessMessage bookingSuccessMessage = new BookingSuccessMessage(new ConfirmBookingRequest(), jsonResponse, 'Sent');
        TelegramClient.sendTelegramMessage(bookingSuccessMessage.toString())
                .whenComplete((result, error) -> {
                    if (error != null) {
                        AppLogger.error(TAG, "Failed to send message: " + error.getMessage(), error);
                    } else {
                        AppLogger.info(TAG, "Message sent successfully!");
                    }
                });*/

        // Test Booking Cancel Message
        TourCMSBooking tourCMSBooking = new TourCMSBooking();
        tourCMSBooking.setBookingId("BKTEST123456789");
        tourCMSBooking.setNote("Cancel booking by BóKun!");
        tourCMSBooking.setCancelReason("23");

        BookingCancelMessage bookingCancelMessage = new BookingCancelMessage(tourCMSBooking);
        TelegramClient.sendTelegramMessage(bookingCancelMessage.toString())
                .whenComplete((result, error) -> {
                    if (error != null) {
                        AppLogger.error(TAG, "Failed to send message: " + error.getMessage(), error);
                    } else {
                        AppLogger.info(TAG, "Message sent successfully!");
                    }
                });
    }
}
