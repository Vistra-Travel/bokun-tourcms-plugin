package io.bokun.inventory.plugin.tourcms.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import io.bokun.inventory.plugin.api.rest.BasicProductInfo;
import io.bokun.inventory.plugin.api.rest.PricingCategory;
import io.bokun.inventory.plugin.tourcms.api.TourCmsClient;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class Mapping {
    private static final String TAG = Mapping.class.getSimpleName();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<PricingCategory> parsePriceCategory(JsonNode node) {
        JsonNode pricesNode = node.path("new_booking").path("people_selection").path("rate");
        List<PricingCategory> prices = new ArrayList<>();
        if (pricesNode.isArray()) {
            for (JsonNode priceNote : pricesNode) {
                PricingCategory pricesCategory = new PricingCategory();
                String label1 = priceNote.path("label_1").asText();
                String label2 = priceNote.path("label_2").asText();
                pricesCategory.setId(label1);
                pricesCategory.setLabel(!label2.isEmpty() ? String.format("%s %s", label1, label2) : label1);
                pricesCategory.setMinAge(priceNote.get("agerange_min").asInt());
                pricesCategory.setMaxAge(priceNote.get("agerange_max").asInt());
                prices.add(pricesCategory);
            }
        }

        return prices;
    }

    public static List<BasicProductInfo> mapProductsList(TourCmsClient tourCmsClient, JsonNode node) {
        List<BasicProductInfo> products = new ArrayList<>();
        JsonNode productsList = node.get("tour");

        if (productsList == null || productsList.isEmpty()) {
            return products;
        }

        List<JsonNode> productNodes = productsList.isArray() ?
                ImmutableList.copyOf(productsList) :
                ImmutableList.of(productsList);

        for (JsonNode productNode : productNodes) {
            BasicProductInfo basicProductInfo = new BasicProductInfo();
            basicProductInfo.setId(productNode.path("tour_id").asText());
            basicProductInfo.setName(productNode.path("tour_name").asText());
            basicProductInfo.setDescription(productNode.path("shortdesc").asText());

            try {
                String productJson = tourCmsClient.getProduct(basicProductInfo.getId(), true);
                JsonNode productDetailNode = MAPPER.readTree(productJson);
                JsonNode product = productDetailNode.get("tour");
                basicProductInfo.setPricingCategories(parsePriceCategory(product));
            } catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {
                AppLogger.error(TAG, String.format("Failed to get product id: %s", basicProductInfo.getId()), e);
                PricingCategory fromPrice = new PricingCategory();
                fromPrice.setId(productNode.path("tour_id").asText() + "_" + productNode.path("from_price").asText());
                fromPrice.setLabel(productNode.path("from_price_display").asText());
                basicProductInfo.setPricingCategories(ImmutableList.of(fromPrice));
            }

            basicProductInfo.setCities(ImmutableList.of(productNode.path("location").asText()));
            basicProductInfo.setCountries(ImmutableList.of(productNode.path("country").asText()));

            products.add(basicProductInfo);
        }

        return products;
    }
}
