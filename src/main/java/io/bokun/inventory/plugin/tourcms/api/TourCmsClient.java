package io.bokun.inventory.plugin.tourcms.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.bokun.inventory.plugin.tourcms.util.AppLogger;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import okhttp3.*;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class TourCmsClient {
    private static final String TAG = "TourCmsClient";
    private static final String BASE_URL = "https://api.tourcms.com";
    private static final String ALGORITHM = "HmacSha256";

    private final OkHttpClient client;
    public final String marketplaceId;
    public final String channelId;
    public final String apiKey;

    private final XmlMapper xmlMapper;
    private final ObjectMapper objectMapper;

    public TourCmsClient() {
        this(null, null, null);
    }

    public TourCmsClient(String marketplaceId, String channelId, String apiKey) {
        this.client = new OkHttpClient();
        this.marketplaceId = marketplaceId != null ? marketplaceId : System.getenv("TOURCMS_MARKETPLACE_ID");
        this.channelId = channelId != null ? channelId : System.getenv("TOURCMS_CHANNEL_ID");
        this.apiKey = apiKey != null ? apiKey : System.getenv("TOURCMS_API_KEY");

        this.xmlMapper = new XmlMapper();
        this.objectMapper = new ObjectMapper();
    }

    private String generateSignature(String marketplaceId, String key, String path, String verb, String channel, long time, String queryString) throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        String toSign = channel + "/" + marketplaceId + "/" + verb + "/" + (time / 1000) + path;
        if (!queryString.isEmpty()) {
            toSign += "?" + queryString;
        }
        Mac mac = Mac.getInstance("HmacSha256");
        SecretKeySpec secret = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        mac.init(secret);
        byte[] shaDigest = mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8));
        String result = new String(Base64.encodeBase64(shaDigest));
        return URLEncoder.encode(result, String.valueOf(StandardCharsets.UTF_8));
    }

    private String buildQueryString(Map<String, Object> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return "";
        }

        return queryParams.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    try {
                        return URLEncoder.encode(entry.getKey(), "UTF-8") + "=" + URLEncoder.encode(String.valueOf(entry.getValue()), "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException("Encoding not supported", e);
                    }
                })
                .collect(Collectors.joining("&"));
    }

    private Response buildRequest(String endpoint, String method, Map<String, Object> queryParams, String xmlBody) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        String fullUrl = BASE_URL + endpoint;

        String queryString = buildQueryString(queryParams);

        if (!queryString.isEmpty()) {
            fullUrl += "?" + queryString;
        }

        long timestamp = System.currentTimeMillis();
        Date dt = new Date(timestamp);
        SimpleDateFormat ft = new SimpleDateFormat ("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
        ft.setTimeZone(TimeZone.getTimeZone("GMT"));
        String currDate = ft.format(dt);

        String signature = generateSignature(marketplaceId, apiKey, endpoint, method, channelId, timestamp, queryString);

        if (signature == null) {
            throw new IOException("Failed to generate signature");
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(fullUrl)
                .addHeader("x-tourcms-date", currDate)
                .addHeader("Authorization", "TourCMS " + channelId + ":" + marketplaceId + ":" + signature)
                .addHeader("Accept", "application/xml")
                .addHeader("Content-Type", "text/xml;charset='utf-8'");

        if (xmlBody != null && !xmlBody.isEmpty()) {
            RequestBody body = RequestBody.create(xmlBody, MediaType.parse("application/xml"));
            if ("POST".equalsIgnoreCase(method)) {
                requestBuilder.post(body);
            } else if ("PUT".equalsIgnoreCase(method)) {
                requestBuilder.put(body);
            }
        } else {
            if (method.equalsIgnoreCase("DELETE")) {
                requestBuilder.delete();
            } else {
                requestBuilder.get();
            }
        }

        return client.newCall(requestBuilder.build()).execute();
    }

    private String resultResponse(Response response) throws IOException {
        assert response.body() != null;
        String xmlResponse = response.body().string();
        Object data = xmlMapper.readValue(xmlResponse, Object.class);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
    }

    public String getProducts() throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        return getProducts(null);
    }

    public String getProducts(Map<String, Object> query) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        try (Response response = buildRequest("/c/tours/search.xml", "GET", query, null)) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch products: " + response.message());
            }
            return resultResponse(response);
        }
    }

    public String getProductsByDate(Map<String, Object> query) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        try (Response response = buildRequest("/c/tour/datesprices/datesndeals/search.xml", "GET", query, null)) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch products by date: " + response.message());
            }
            return resultResponse(response);
        }
    }

    public String getProduct(String id, boolean showOptions) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        if (showOptions) {
            params.put("show_options", 1);
        }
        return getProduct(params);
    }

    public String getProduct(Map<String, Object> query) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        try (Response response = buildRequest("/c/tour/show.xml", "GET", query, null)) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch products: " + response.message());
            }
            return resultResponse(response);
        }
    }

    public String updateTour(String tourXml) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        try (Response response = buildRequest("/c/tour/update.xml", "POST", null, tourXml)) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to update tour: " + response.message());
            }
            return resultResponse(response);
        }
    }

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        TourCmsClient tourCmsClient = new TourCmsClient();

//        String product = tourCmsClient.getProduct("48", true);
//        AppLogger.info(TAG, String.format(" - Response: %s", product));

        Map<String, Object> params = new HashMap<>();
        params.put("per_page", 200);
        params.put("tour_id", 48);
        AppLogger.info(TAG, String.format("Get all products: %s - %s - %s", tourCmsClient.apiKey, tourCmsClient.marketplaceId, tourCmsClient.channelId));
        String products = tourCmsClient.getProducts(params);
        AppLogger.info(TAG, String.format(" - Response: %s", products));
    }
}
