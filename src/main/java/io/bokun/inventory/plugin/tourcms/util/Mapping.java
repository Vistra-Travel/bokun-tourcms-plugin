package io.bokun.inventory.plugin.tourcms.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import io.bokun.inventory.plugin.api.rest.*;
import io.bokun.inventory.plugin.tourcms.api.TourCmsClient;
import io.bokun.inventory.plugin.tourcms.model.ProductRateMapping;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Mapping {
    private static final String TAG = Mapping.class.getSimpleName();
    public static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Rate DEFAULT_RATE = new Rate().id("standard_rate").label("Standard Rate");

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
                departuresParams.put("per_page", 30);
                String departuresResponse = tourCmsClient.getTourDepartures(departuresParams);
                // AppLogger.info(TAG, String.format("TourCMS - getTourDepartures %s JSON: %s", departuresParams, Mapping.MAPPER.writeValueAsString(Mapping.MAPPER.readTree(departuresResponse))));
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

    public static void addRateIfNotExist(JsonNode rateNode, List<RateWithPrice> rateList, String currency, boolean isMainRate) {
        String rateId = rateNode.path("rate_id").asText();
        boolean exists = rateList.stream().anyMatch(rate -> rate.getRateId().equals(rateId));

        if (!exists) {
            RateWithPrice rate = new RateWithPrice();
            rate.setRateId(rateId);

            PricePerPerson pricePerPerson = new PricePerPerson();
            pricePerPerson.setPricingCategoryWithPrice(new ArrayList<>());

            PricingCategoryWithPrice categoryPrice = new PricingCategoryWithPrice();
            categoryPrice.setPricingCategoryId(rateId);

            Price price = new Price();
            price.setAmount(rateNode.path("rate_price").asText());
            price.setCurrency(currency);

            categoryPrice.setPrice(price);

            pricePerPerson.getPricingCategoryWithPrice().add(categoryPrice);
            rate.setPricePerPerson(pricePerPerson);

            if (isMainRate) {
                PricePerBooking pricePerBooking = new PricePerBooking();
                pricePerBooking.setPrice(price);
                rate.setPricePerBooking(pricePerBooking);
            }

            rateList.add(rate);
        }
    }

    public static ProductRateMapping getProductRates(String id, TourCmsClient tourCmsClient) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        Map<String, Object> departuresParams = new HashMap<>();
        departuresParams.put("id", id);
        departuresParams.put("per_page", 30);
        String tourShowResponse = tourCmsClient.getTour(id, true);
        String tourDeparturesResponse = tourCmsClient.getTourDepartures(departuresParams);
        return parseProductRates(tourShowResponse, tourDeparturesResponse);
    }

    public static ProductRateMapping parseProductRates(String tourShowResponse, String tourDeparturesResponse) throws JsonProcessingException {
        List<Rate> rates = new ArrayList<>();
        List<String> startTimes = new ArrayList<>();
        List<PricingCategory> priceCategories = new ArrayList<>();

        JsonNode tourShowNode = Mapping.MAPPER.readTree(tourShowResponse);
        JsonNode tourDeparturesNode = Mapping.MAPPER.readTree(tourDeparturesResponse);
        JsonNode newBookingRates = tourShowNode.path("tour").path("new_booking").path("people_selection").path("rate");
        JsonNode tourDepartures = tourDeparturesNode.path("tour").path("dates_and_prices").path("departure");

        List<JsonNode> newBookingRatesList = newBookingRates.isArray() ?
                ImmutableList.copyOf(newBookingRates) :
                ImmutableList.of(newBookingRates);

        List<JsonNode> tourDeparturesList = tourDepartures.isArray() ?
                ImmutableList.copyOf(tourDepartures) :
                ImmutableList.of(tourDepartures);

        if (!newBookingRatesList.isEmpty()) {
            for (JsonNode rateNode : newBookingRatesList) {
                String rateId = rateNode.path("rate_id").asText();
                String rateLabel = rateNode.path("label_1").asText();

                if (!rateId.isEmpty() && !rateLabel.isEmpty() && rates.stream().noneMatch(r -> r.getId().equals(rateId))) {
                    Rate rate = new Rate();
                    rate.setId(rateId);
                    rate.setLabel(rateLabel);
                    rates.add(rate);
                }
            }
        }

        if (!tourDeparturesList.isEmpty()) {
            for (JsonNode departure : tourDeparturesList) {
                String startTime = departure.path("start_time").asText();
                if (!startTimes.contains(startTime)) {
                    startTimes.add(startTime);
                }

                JsonNode mainPriceNode = departure.path("main_price");
                JsonNode extraPriceNode = departure.path("extra_rates").path("rate");

                List<JsonNode> mainPriceNodeArray = mainPriceNode.isArray() ?
                        new ArrayList<>(ImmutableList.copyOf(mainPriceNode)) :
                        new ArrayList<>(ImmutableList.of(mainPriceNode));
                List<JsonNode> extraPriceNodeArray = extraPriceNode.isArray() ?
                        ImmutableList.copyOf(extraPriceNode) :
                        ImmutableList.of(extraPriceNode);

                mainPriceNodeArray.addAll(extraPriceNodeArray);
                priceCategories = Mapping.parsePriceCategoryFromNodeList(mainPriceNodeArray);
            }
        }

        if (rates.isEmpty()) {
            rates.addAll(ImmutableList.of(DEFAULT_RATE));
        }

        startTimes = startTimes.stream()
                .filter(item -> item != null && !item.trim().isEmpty())
                .collect(Collectors.toList());

        return new ProductRateMapping(rates, startTimes, priceCategories);
    }
}
