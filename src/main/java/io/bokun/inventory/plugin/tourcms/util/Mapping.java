package io.bokun.inventory.plugin.tourcms.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import io.bokun.inventory.plugin.api.rest.BasicProductInfo;
import io.bokun.inventory.plugin.api.rest.PricingCategory;
import io.bokun.inventory.plugin.api.rest.Rate;
import io.bokun.inventory.plugin.tourcms.api.TourCmsClient;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Mapping {
    private static final String TAG = Mapping.class.getSimpleName();
    public static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<PricingCategory> parsePriceCategoryFromTourNode(JsonNode node) {
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

    public static List<PricingCategory> parsePriceCategoryFromNodeList(List<JsonNode> arrayNode) {
        List<PricingCategory> prices = new ArrayList<>();
        for (JsonNode priceNote : arrayNode) {
            String rateId = priceNote.path("rate_id").asText();
            String rateName = priceNote.path("rate_name").asText();
            int minAge = !priceNote.path("agerange_min").isEmpty() ? priceNote.path("agerange_min").asInt() : 0;
            int maxAge = !priceNote.path("agerange_max").isEmpty() ? priceNote.path("agerange_max").asInt() : 0;

            if (prices.stream().noneMatch(p -> p.getId().equals(rateId))) {
                PricingCategory pricesCategory = new PricingCategory();
                pricesCategory.setId(rateId);
                pricesCategory.setLabel(rateName);
                pricesCategory.setMinAge(minAge);
                pricesCategory.setMaxAge(maxAge);
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
                Map<String, Object> departuresParams = new HashMap<>();
                departuresParams.put("id", basicProductInfo.getId());
                departuresParams.put("per_page", 20);
                String departuresResponse = tourCmsClient.getTourDepartures(departuresParams);
                AppLogger.info(TAG, String.format("TourCMS - getTourDepartures %s JSON: %s", departuresParams, Mapping.MAPPER.writeValueAsString(Mapping.MAPPER.readTree(departuresResponse))));
                JsonNode departuresNode = Mapping.MAPPER.readTree(departuresResponse);
                JsonNode tourDepartureNode = departuresNode.path("tour").path("dates_and_prices").path("departure");
                List<JsonNode> tourDepartureNodes = tourDepartureNode.isArray() ?
                        ImmutableList.copyOf(tourDepartureNode) :
                        ImmutableList.of(tourDepartureNode);

                if (!tourDepartureNodes.isEmpty()) {
                    for (JsonNode departure : tourDepartureNodes) {
                        JsonNode mainPriceNode = departure.path("main_price");
                        JsonNode extraPriceNode = departure.path("extra_rates").path("rate");
                        List<JsonNode> mainPriceNodeArray = mainPriceNode.isArray() ?
                                new ArrayList<>(ImmutableList.copyOf(mainPriceNode)) :
                                new ArrayList<>(ImmutableList.of(mainPriceNode));
                        List<JsonNode> extraPriceNodeArray = extraPriceNode.isArray() ?
                                ImmutableList.copyOf(extraPriceNode) :
                                ImmutableList.of(extraPriceNode);
                        mainPriceNodeArray.addAll(extraPriceNodeArray);
                        basicProductInfo.setPricingCategories(parsePriceCategoryFromNodeList(mainPriceNodeArray));
                    }
                }

//                String productJson = tourCmsClient.getTour(basicProductInfo.getId(), true);
//                JsonNode productDetailNode = MAPPER.readTree(productJson);
//                JsonNode product = productDetailNode.get("tour");
//                basicProductInfo.setPricingCategories(parsePriceCategoryFromTourNode(product));
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
