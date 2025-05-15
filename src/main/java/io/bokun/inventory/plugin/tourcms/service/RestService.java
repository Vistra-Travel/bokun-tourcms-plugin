package io.bokun.inventory.plugin.tourcms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.squareup.okhttp.OkHttpClient;
import io.bokun.inventory.plugin.api.rest.*;
import io.bokun.inventory.plugin.tourcms.Configuration;
import io.bokun.inventory.plugin.tourcms.api.TelegramClient;
import io.bokun.inventory.plugin.tourcms.api.TourCmsClient;
import io.bokun.inventory.plugin.tourcms.model.*;
import io.bokun.inventory.plugin.tourcms.util.AppLogger;
import io.bokun.inventory.plugin.tourcms.util.EmailSender;
import io.bokun.inventory.plugin.tourcms.util.Mapping;
import io.undertow.server.HttpServerExchange;

import javax.annotation.Nonnull;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.bokun.inventory.plugin.api.rest.PluginCapability.*;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static java.util.concurrent.TimeUnit.SECONDS;

public class RestService {

    private static final String TAG = RestService.class.getSimpleName();
    private static final long DEFAULT_READ_TIMEOUT = 30L;

    private final OkHttpClient client;

    @Inject
    public RestService() {
        this.client = new OkHttpClient();
        client.setReadTimeout(DEFAULT_READ_TIMEOUT, SECONDS);
    }

    private PluginConfigurationParameter asStringParameter(String name, boolean required) {
        PluginConfigurationParameter param = new PluginConfigurationParameter();
        param.setName(name);
        param.setType(PluginParameterDataType.STRING);
        param.setRequired(required);
        return param;
    }

    private PluginConfigurationParameter asRequiredLongParameter(String name) {
        PluginConfigurationParameter param = new PluginConfigurationParameter();
        param.setName(name);
        param.setType(PluginParameterDataType.LONG);
        param.setRequired(true);
        return param;
    }

    public void getDefinition(@Nonnull HttpServerExchange exchange) {
        PluginDefinition definition = new PluginDefinition();
        definition.setName("TourCMS REST plugin");
        definition.setDescription("Provides availability and accepts bookings into Bókun booking system. Uses REST protocol");

        definition.getCapabilities().add(AVAILABILITY);

        // below entry should be commented out if the plugin only supports reservation & confirmation as a single step
        definition.getCapabilities().add(RESERVATIONS);
        definition.getCapabilities().add(RESERVATION_CANCELLATION);
        // definition.getCapabilities().add(AMENDMENT);

        definition.getParameters().add(asStringParameter(Configuration.TOURCMS_ACCOUNT_ID, true));
        definition.getParameters().add(asStringParameter(Configuration.TOURCMS_CHANNEL_ID, true));

        definition.getParameters().add(asStringParameter(Configuration.TOURCMS_PRIVATE_KEY, false));
        definition.getParameters().add(asStringParameter(Configuration.TOURCMS_FILTER_IDS, false));

        definition.getParameters().add(asStringParameter(Configuration.SMTP_SERVER, true));
        definition.getParameters().add(asStringParameter(Configuration.SMTP_USERNAME, true));
        definition.getParameters().add(asStringParameter(Configuration.SMTP_PASSWORD, true));
        definition.getParameters().add(asStringParameter(Configuration.MAIL_CC, false));

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(definition));
    }

    public void searchProducts(@Nonnull HttpServerExchange exchange) {
        AppLogger.info(TAG, "Search products!");
        SearchProductRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), SearchProductRequest.class);
        String requestJson = new Gson().toJson(request);
        AppLogger.info(TAG, String.format("- Request: %s", requestJson));

        Configuration configuration = Configuration.fromRestParameters(request.getParameters());
        TourCmsClient tourCmsClient = new TourCmsClient(configuration.marketplaceId, configuration.channelId, configuration.getTourcmsPrivateKey());

        List<BasicProductInfo> products = new ArrayList<>();
        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        AppLogger.info(TAG, String.format("Start fetching products from TourCMS: %s - %s - %s", tourCmsClient.marketplaceId, tourCmsClient.channelId, tourCmsClient.apiKey));

        String data = "";
        Map<String, Object> params = new HashMap<>();
        params.put("per_page", 200);

        if (configuration.filterIds != null && !configuration.filterIds.isEmpty()) {
            params.put("tour_id", configuration.filterIds);
        }
        try {
            data = tourCmsClient.getTours(params);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException exception) {
            AppLogger.error(TAG, "Couldn't get products", exception);
        }

        if (data == null || data.isEmpty()) {
            AppLogger.info(TAG, String.format("Empty res data: %s", data));
            exchange.getResponseSender().send(new Gson().toJson(products));
            return;
        }

        int totalProducts = 0;
        try {
            JsonNode dataNode = Mapping.MAPPER.readTree(data);
            totalProducts = dataNode.get("total_tour_count").asInt();
            products = Mapping.mapProductsList(tourCmsClient, dataNode);
        } catch (JsonProcessingException e) {
            AppLogger.error(TAG, "Couldn't process products", e);
        }

        AppLogger.info(TAG, String.format(" - Return: %s products", totalProducts));
        exchange.getResponseSender().send(new Gson().toJson(products));
    }

    public void getProductById(HttpServerExchange exchange) {
        GetProductByIdRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), GetProductByIdRequest.class);
        AppLogger.info(TAG, String.format("Get product by id: %s", request.getExternalId()));
        String requestJson = new Gson().toJson(request);
        AppLogger.info(TAG, String.format("- Request: %s", requestJson));

        Configuration configuration = Configuration.fromRestParameters(request.getParameters());
        TourCmsClient tourCmsClient = new TourCmsClient(configuration.marketplaceId, configuration.channelId, configuration.getTourcmsPrivateKey());

        String id = request.getExternalId();
        if (id == null || id.isEmpty()) {
            String msg = String.format("Empty request external id: %s", id);
            AppLogger.info(TAG, msg);
            exchange.setStatusCode(400);
            exchange.getResponseSender().send("{'message':'" + msg + "'}");
        }

        Map<String, Object> tourDeparturesParams = new HashMap<>();
        tourDeparturesParams.put("id", id);
        tourDeparturesParams.put("per_page", 30);

        try {
            String tourShowResponse = tourCmsClient.getTour(id, true);
//            AppLogger.info(TAG, String.format("TourCMS - getTour ID %s JSON: %s", id, Mapping.MAPPER.writeValueAsString(Mapping.MAPPER.readTree(productJson))));
            String tourDeparturesResponse = tourCmsClient.getTourDepartures(tourDeparturesParams);
            ProductRateMapping productRateMapping = Mapping.parseProductRates(tourDeparturesResponse);
            List<String> startTimes = productRateMapping.getStartTimes();

            JsonNode productNode = Mapping.MAPPER.readTree(tourShowResponse);
            JsonNode product = productNode.get("tour");

            ProductDescription description = new ProductDescription();
            // 1. id
            description.setId(product.get("tour_id").asText());
            // 2. name
            description.setName(product.get("tour_name").asText());
            // 3. description
            description.setDescription(product.get("shortdesc").asText());

            // 3. pricingCategories
            description.setPricingCategories(productRateMapping.getPriceCategories());
            // 4. rates
            description.setRates(productRateMapping.getRates());
            // 5. bookingType
            if (!startTimes.isEmpty()) {
                description.setBookingType(BookingType.DATE_AND_TIME);
            } else {
                JsonNode dateType = product.path("new_booking").path("date_selection").path("date_type");
                BookingType bookingType = BookingType.DATE;
                if (dateType.isTextual()) {
                    try {
                        bookingType = BookingType.valueOf(dateType.asText());
                        AppLogger.info(TAG, "Mapped booking type to: " + bookingType);
                    } catch (IllegalArgumentException e) {
                        AppLogger.warn(TAG, "Unknown booking type found in XML: " + dateType.asText() + ". Defaulting to DATE.");
                    }
                } else {
                    AppLogger.warn(TAG, "date_type is not textual or missing. Defaulting to DATE.");
                }
                description.setBookingType(bookingType);
            }

            // 6. dropoffAvailable
            JsonNode pickupOnRequest = product.path("pickup_on_request");
            JsonNode pickupPoints = product.path("pickup_points");
            boolean dropoffAvailable = pickupOnRequest.asInt() == 1 && pickupPoints.isArray() && !pickupPoints.isEmpty();
            description.setDropoffAvailable(dropoffAvailable);

            // 7. dropoffPlaces
            if (dropoffAvailable) {
                List<PickupDropoffPlace> pickupDropoffPlaces = new ArrayList<>();

                for (JsonNode point : pickupPoints) {
                    PickupDropoffPlace place = new PickupDropoffPlace();
                    Address address = new Address();

                    // Map thông tin từ XML sang Address
                    address.setAddressLine1(point.path("address1").asText());
                    address.setAddressLine2(point.path("address2").asText());
                    address.setPostalCode(point.path("postcode").asText());

                    // Lấy thông tin city và countryCode từ productNode (XML response)
                    String city = product.path("location").asText(); // "location" trong XML là tên thành phố
                    String countryCode = product.path("country").asText(); // "country" trong XML là mã quốc gia

                    address.setCity(city);
                    address.setCountryCode(countryCode);

                    // Lấy geocode từ XML response
                    String geocode = point.path("geocode").asText();
                    if (!geocode.isEmpty()) {
                        String[] geoParts = geocode.split(",");
                        if (geoParts.length == 2) {
                            GeoPoint geoPoint = new GeoPoint();
                            geoPoint.setLatitude(Double.parseDouble(geoParts[0]));
                            geoPoint.setLongitude(Double.parseDouble(geoParts[1]));
                            address.setGeoPoint(geoPoint);
                        }
                    }

                    // Set title
                    place.setTitle(point.path("pickup_name").asText());
                    place.setAddress(address);

                    // Add vào list
                    pickupDropoffPlaces.add(place);
                }

                description.setDropoffPlaces(pickupDropoffPlaces);
                description.setCustomDropoffPlaceAllowed(false);
            }

            // 8. productCategory
            description.setProductCategory(ProductCategory.ACTIVITIES);

            // 9. ticketSupport
            // Accommodation → TICKETS_NOT_REQUIRED
            // Activities → TICKET_PER_PERSON
            // Car Rentals → TICKETS_NOT_REQUIRED
            // Transport → TICKET_PER_BOOKING
            List<TicketSupport> ticketSupportList = new ArrayList<>();
            ticketSupportList.add(TicketSupport.TICKET_PER_PERSON);
            description.setTicketSupport(ticketSupportList);

            // 10. countries
            description.setCountries(ImmutableList.of(product.get("country").asText()));

            // 11. cities
            description.setCities(ImmutableList.of(product.get("location").asText()));

            // 12. startTimes
            if (description.getBookingType().equals(BookingType.DATE_AND_TIME)) {
                JsonNode startTimeNode = product.path("start_time");
                if (startTimeNode.isTextual() && startTimeNode.asText().contains(":") && !startTimes.contains(startTimeNode.asText())) {
                    startTimes.add(startTimeNode.asText());
                }
                startTimes.sort((time1, time2) -> {
                    String[] parts1 = time1.split(":");
                    String[] parts2 = time2.split(":");

                    int hour1 = Integer.parseInt(parts1[0]);
                    int minute1 = Integer.parseInt(parts1[1]);
                    int hour2 = Integer.parseInt(parts2[0]);
                    int minute2 = Integer.parseInt(parts2[1]);

                    if (hour1 == hour2) {
                        return Integer.compare(minute1, minute2);
                    }
                    return Integer.compare(hour1, hour2);
                });
                List<Time> startTimesList = new ArrayList<>();
                for (String startTime : startTimes) {
                    String[] timeParts = startTime.split(":");
                    int hour = Integer.parseInt(timeParts[0]);
                    int minute = Integer.parseInt(timeParts[1]);
                    Time time = new Time();
                    time.setHour(hour);
                    time.setMinute(minute);
                    startTimesList.add(time);
                }
                description.setStartTimes(startTimesList);
            }

            // 13. ticketType
            JsonNode deliveryFormat = product.path("delivery_formats").path("delivery_format");
            TicketType ticketType = TicketType.QR_CODE;
            if (!deliveryFormat.isEmpty()) {
                try {
                    ticketType = TicketType.valueOf(deliveryFormat.asText());
                } catch (IllegalArgumentException e) {
                    AppLogger.error(TAG, String.format("Couldn't parse ticketType: %s", deliveryFormat.asText()), e);
                }
            }
            description.setTicketType(ticketType);

            // 14. meetingType
            MeetingType meetingType;
            boolean hasPickupPoints = product.path("pickup_points").isArray() && !product.path("pickup_points").isEmpty();
            boolean hasPickupOnRequest = product.path("pickup_on_request").asInt(0) == 1;
            if (hasPickupPoints) {
                if (hasPickupOnRequest) {
                    meetingType = MeetingType.MEET_ON_LOCATION_OR_PICK_UP;
                } else {
                    meetingType = MeetingType.PICK_UP;
                }
            } else {
                meetingType = MeetingType.MEET_ON_LOCATION;
            }
            description.setMeetingType(meetingType);

            // 15. customPickupPlaceAllowed
            boolean customPickupPlaceAllowed = false;
            // Nếu meetingType là MEET_ON_LOCATION_OR_PICK_UP hoặc PICK_UP thì cần check
            if (meetingType == MeetingType.MEET_ON_LOCATION_OR_PICK_UP || meetingType == MeetingType.PICK_UP) {
                customPickupPlaceAllowed = hasPickupOnRequest;
            }
            description.setCustomPickupPlaceAllowed(customPickupPlaceAllowed);

            // 16. pickupMinutesBefore
            Integer pickupMinutesBefore = null;
            // Chỉ lấy giá trị nếu là MEET_ON_LOCATION_OR_PICK_UP hoặc PICK_UP
            if (meetingType == MeetingType.MEET_ON_LOCATION_OR_PICK_UP || meetingType == MeetingType.PICK_UP) {
                JsonNode pickupNode = product.path("pickup_minutes_before");
                if (pickupNode.isInt()) {
                    pickupMinutesBefore = pickupNode.asInt();
                }
            }
            description.setPickupMinutesBefore(pickupMinutesBefore);

            // 17. pickupPlaces
            List<PickupDropoffPlace> pickupDropoffPlaces = new ArrayList<>();
            if (meetingType == MeetingType.MEET_ON_LOCATION_OR_PICK_UP || meetingType == MeetingType.PICK_UP) {
                JsonNode pickupPointsNode = product.path("pickup_points");
                if (pickupPointsNode.isArray()) {
                    for (JsonNode point : pickupPointsNode) {
                        PickupDropoffPlace place = new PickupDropoffPlace();
                        Address address = new Address();

                        // Map các trường thông tin
                        place.setTitle(point.path("pickup_name").asText());

                        address.setAddressLine1(point.path("address1").asText());
                        address.setAddressLine2(point.path("address2").asText());
                        address.setCity(product.path("location").asText());
                        address.setCountryCode(product.path("country").asText());

                        // Geolocation nếu có
                        String geocode = point.path("geocode").asText();
                        if (geocode.contains(",")) {
                            String[] coordinates = geocode.split(",");
                            GeoPoint geoPoint = new GeoPoint();
                            geoPoint.setLatitude(Double.parseDouble(coordinates[0]));
                            geoPoint.setLongitude(Double.parseDouble(coordinates[1]));
                            address.setGeoPoint(geoPoint);
                        }

                        place.setAddress(address);
                        pickupDropoffPlaces.add(place);
                    }
                }
            }
            description.setPickupPlaces(pickupDropoffPlaces);

            // 18. Extra
            List<Extra> extras = new ArrayList<>();
            JsonNode alternativeTours = product.path("options").path("option");
            if (!alternativeTours.isEmpty()) {
                List<JsonNode> alternativeToursList = alternativeTours.isArray() ?
                        ImmutableList.copyOf(alternativeTours) :
                        ImmutableList.of(alternativeTours);
                for (JsonNode item : alternativeToursList) {
                    Extra extra = new Extra();
                    extra.setId(item.get("option_id").asText());
                    extra.setTitle(item.get("option_name").asText());
                    extra.setDescription(item.get("short_description").asText());
                    extra.setOptional(true);                  // Giả định là tất cả đều optional
                    extra.setMaxPerBooking(1);                // Giả định chỉ đặt 1 lần mỗi booking
                    extra.setLimitByPax(false);               // Không giới hạn theo số người
                    extra.setIncreasesCapacity(false);        // Không tăng số lượng người cho phép
                    extras.add(extra);
                }
            }
            description.setExtras(ImmutableList.copyOf(extras));

            exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
            String response = new Gson().toJson(description);
            AppLogger.info(TAG, String.format("-> Response: %s", response));
            exchange.getResponseSender().send(response);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException exception) {
            AppLogger.error(TAG, "Couldn't get product", exception);
            exchange.setStatusCode(500);
            exchange.getResponseSender().send("{'message':'Could not get product'}");
        }
    }

    public void getAvailableProducts(HttpServerExchange exchange) {
        ProductsAvailabilityRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), ProductsAvailabilityRequest.class);
        AppLogger.info(TAG, String.format("Get available products: %s", request.getExternalProductIds()));
        String requestJson = new Gson().toJson(request);
        AppLogger.info(TAG, String.format("- Request: %s", requestJson));

        Configuration configuration = Configuration.fromRestParameters(request.getParameters());
        TourCmsClient tourCmsClient = new TourCmsClient(configuration.marketplaceId, configuration.channelId, configuration.getTourcmsPrivateKey());

        DatePeriod range = request.getRange();
        long requiredCapacity = request.getRequiredCapacity();
        List<String> externalProductIds = request.getExternalProductIds();
        String startDate = String.format("%04d-%02d-%02d", range.getFrom().getYear(), range.getFrom().getMonth(), range.getFrom().getDay());
        String endDate = String.format("%04d-%02d-%02d", range.getTo().getYear(), range.getTo().getMonth(), range.getTo().getDay());

        List<String> filterIds = (configuration.filterIds != null && !configuration.filterIds.isEmpty())
                ? Arrays.asList(configuration.filterIds.split(","))
                : new ArrayList<>();

        List<String> allowExternalProductIds = externalProductIds.stream()
                .filter(id -> filterIds.isEmpty() || filterIds.contains(id))
                .collect(Collectors.toList());

        List<String> notAllowExternalProductIds = externalProductIds.stream()
                .filter(id -> !allowExternalProductIds.contains(id))
                .collect(Collectors.toList());

        List<ProductsAvailabilityResponse> productsAvailabilityResponses = new ArrayList<>();

        notAllowExternalProductIds.forEach(productId -> {
            AppLogger.info(TAG, String.format("- Product ID %s is not allow in %s -> Set false", "TOURCMS_FILTER_IDS", productId));
            productsAvailabilityResponses.add(new ProductsAvailabilityResponse()
                    .productId(productId)
                    .actualCheckDone(false)
            );
        });

        allowExternalProductIds.forEach(productId -> {
            AppLogger.info(TAG, String.format("- Checking for product ID: %s", productId));

            Map<String, Object> params = new HashMap<>();
            params.put("id", productId);
            params.put("distinct_start_dates", 1);
            params.put("startdate_start", startDate);
            params.put("startdate_end", endDate);

            try {
                String toursByDatesResponse = tourCmsClient.getToursByDates(params);
//                AppLogger.info(TAG, String.format("TourCMS - getToursByDates %s JSON: %s", params, Mapping.MAPPER.writeValueAsString(Mapping.MAPPER.readTree(toursResponse))));

                JsonNode datesNode = Mapping.MAPPER.readTree(toursByDatesResponse)
                        .path("dates_and_prices")
                        .path("date");

                long minCapacity = StreamSupport.stream(datesNode.spliterator(), false)
                        .filter(date -> "OPEN".equals(date.path("status").asText()))
                        .map(date -> date.path("spaces_remaining").asText())
                        .filter(value -> !value.isEmpty() && value.matches("\\d+")) // Chỉ lấy giá trị không rỗng và là số
                        .mapToLong(Long::parseLong)
                        .min()
                        .orElse(0);

                AppLogger.info(TAG, String.format("- Product ID: %s -> Min Capacity Found: %d. RequiredCapacity: %d -> %s", productId, minCapacity, requiredCapacity, requiredCapacity <= minCapacity));

                productsAvailabilityResponses.add(new ProductsAvailabilityResponse()
                        .productId(productId)
                        .actualCheckDone(requiredCapacity <= minCapacity)
                );

            } catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {
                AppLogger.error(TAG, String.format("Couldn't get tour by dates: %s", params), e);
                productsAvailabilityResponses.add(new ProductsAvailabilityResponse()
                        .productId(productId)
                        .actualCheckDone(false)
                );
            }
        });

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        String response = new Gson().toJson(productsAvailabilityResponses);
        AppLogger.info(TAG, String.format("-> Response: %s", response));
        exchange.getResponseSender().send(response);
    }

    public void getProductAvailability(HttpServerExchange exchange) {
        ProductAvailabilityRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), ProductAvailabilityRequest.class);
        AppLogger.info(TAG, String.format("Get product availability: %s", request.getProductId()));
        String requestJson = new Gson().toJson(request);
        AppLogger.info(TAG, String.format("- Request: %s", requestJson));

        Configuration configuration = Configuration.fromRestParameters(request.getParameters());
        TourCmsClient tourCmsClient = new TourCmsClient(configuration.marketplaceId, configuration.channelId, configuration.getTourcmsPrivateKey());

        DatePeriod range = request.getRange();
        String productId = request.getProductId();
        String startDateStart = String.format("%04d-%02d-%02d", range.getFrom().getYear(), range.getFrom().getMonth(), range.getFrom().getDay());
        String startDateEnd = String.format("%04d-%02d-%02d", range.getTo().getYear(), range.getTo().getMonth(), range.getTo().getDay());

        Map<String, Object> params = new HashMap<>();
        params.put("id", productId);
        params.put("start_date_start", startDateStart);
        params.put("start_date_end", startDateEnd);

        List<ProductAvailabilityWithRatesResponse> productAvailabilityWithRatesResponses = new ArrayList<>();

        Map<String, ProductAvailabilityWithRatesResponse> responseMap = new HashMap<>();

        try {
            String tourDeparturesResponse = tourCmsClient.getTourDepartures(params);
            ProductRateMapping productRateMapping = Mapping.parseProductRates(tourDeparturesResponse);
            JsonNode tourNode = Mapping.MAPPER.readTree(tourDeparturesResponse).path("tour");

            if (!tourNode.isMissingNode()) {
                JsonNode departuresNode = tourNode.path("dates_and_prices").path("departure");

                List<JsonNode> departuresNodeList = departuresNode.isArray() ?
                        ImmutableList.copyOf(departuresNode) :
                        ImmutableList.of(departuresNode);

                for (JsonNode departure : departuresNodeList) {
                    String startDate = departure.path("start_date").asText(null);
                    String startTime = departure.path("start_time").asText(null);
                    int capacity = departure.path("spaces_remaining").asInt(0);
                    String supplierNote = departure.path("supplier_note").asText();

                    if (startDate != null && startTime != null) {
                        LocalDate date = LocalDate.parse(startDate);
                        DateYMD tourDate = new DateYMD();
                        tourDate.setYear(date.getYear());
                        tourDate.setMonth(date.getMonthValue());
                        tourDate.setDay(date.getDayOfMonth());

                        // Set Time
                        Time time = null;
                        if (startTime.contains(":")) {
                            String[] timeParts = startTime.split(":");
                            time = new Time();
                            time.setHour(Integer.parseInt(timeParts[0]));
                            time.setMinute(Integer.parseInt(timeParts[1]));
                        }

                        // Main Price
                        JsonNode mainPriceNode = departure.path("main_price");
                        if (!mainPriceNode.isMissingNode()) {
                            RateWithPrice mainRate = Mapping.mapRate(productRateMapping, supplierNote, mainPriceNode, tourNode.path("sale_currency").asText(), true);

                            String key = startDate + "_" + startTime + "_" + mainRate.getRateId();
                            ProductAvailabilityWithRatesResponse response = responseMap.getOrDefault(key, new ProductAvailabilityWithRatesResponse());

                            // Cập nhật thông tin cơ bản nếu chưa tồn tại
                            if (response.getDate() == null) {
                                response.setCapacity(capacity);
                                response.setDate(tourDate);
                                response.setTime(time);
                                response.setRates(new ArrayList<>());
                                response.getRates().add(mainRate);
                            } else {
                                // Merge pricingCategoryWithPrice
                                response.getRates().get(0).getPricePerPerson()
                                        .getPricingCategoryWithPrice()
                                        .addAll(mainRate.getPricePerPerson().getPricingCategoryWithPrice());
                            }

                            if (mainRate.getPricePerBooking() != null) {
                                response.getRates().get(0).setPricePerBooking(mainRate.getPricePerBooking());
                            }

                            responseMap.put(key, response);
                        }

                        // Extra Rates
                        JsonNode extraRatesNode = departure.path("extra_rates").path("rate");
                        if (extraRatesNode.isArray()) {
                            for (JsonNode extraRateNode : extraRatesNode) {
                                if (!extraRateNode.isMissingNode()) {
                                    RateWithPrice extraRate = Mapping.mapRate(productRateMapping, supplierNote, extraRateNode, tourNode.path("sale_currency").asText(), false);

                                    String key = startDate + "_" + startTime + "_" + extraRate.getRateId();
                                    ProductAvailabilityWithRatesResponse extraResponse = responseMap.getOrDefault(key, new ProductAvailabilityWithRatesResponse());

                                    if (extraResponse.getDate() == null) {
                                        extraResponse.setCapacity(capacity);
                                        extraResponse.setDate(tourDate);
                                        extraResponse.setTime(time);
                                        extraResponse.setRates(new ArrayList<>());
                                        extraResponse.getRates().add(extraRate);
                                    } else {
                                        // Merge pricingCategoryWithPrice
                                        extraResponse.getRates().get(0).getPricePerPerson()
                                                .getPricingCategoryWithPrice()
                                                .addAll(extraRate.getPricePerPerson().getPricingCategoryWithPrice());
                                    }

                                    responseMap.put(key, extraResponse);
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            AppLogger.error(TAG, String.format("Couldn't get tour by dates: %s", params), e);
        }

        // Sort kết quả
        productAvailabilityWithRatesResponses = new ArrayList<>(responseMap.values());
        productAvailabilityWithRatesResponses.sort(Comparator
                .comparing((ProductAvailabilityWithRatesResponse p) -> p.getDate().getYear())
                .thenComparing(p -> p.getDate().getMonth())
                .thenComparing(p -> p.getDate().getDay())
                .thenComparing(p -> p.getTime().getHour())
                .thenComparing(p -> p.getTime().getMinute())
        );

        productAvailabilityWithRatesResponses = productAvailabilityWithRatesResponses.stream()
                .filter(p -> p.getCapacity() > 0)
                .collect(Collectors.toList());

        // Set header và gửi response
        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        String response = new Gson().toJson(productAvailabilityWithRatesResponses);
        AppLogger.info(TAG, String.format("-> Response: %s", response));
        exchange.getResponseSender().send(response);
    }

    public void createReservation(HttpServerExchange exchange) {
        ReservationRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), ReservationRequest.class);
        AppLogger.info(TAG, "Create reservation");
        String requestJson = new Gson().toJson(request);
        AppLogger.info(TAG, String.format("- Request: %s", requestJson));

        Configuration configuration = Configuration.fromRestParameters(request.getParameters());
        TourCmsClient tourCmsClient = new TourCmsClient(configuration.marketplaceId, configuration.channelId, configuration.getTourcmsPrivateKey());

        // Define response
        ReservationResponse response = new ReservationResponse();
        SuccessfulReservation successfulReservation = new SuccessfulReservation();

        // Define query
        String productId = request.getReservationData().getProductId();
        String date = String.format("%04d-%02d-%02d", request.getReservationData().getDate().getYear(), request.getReservationData().getDate().getMonth(), request.getReservationData().getDate().getDay());
        String startTime = String.format("%02d:%02d", request.getReservationData().getTime().getHour(), request.getReservationData().getTime().getMinute());

        HashMap<String, Object> tourAvailableParams = new HashMap<>();
        tourAvailableParams.put("id", productId);
        tourAvailableParams.put("date", date);
        tourAvailableParams.put("start_time", startTime);
        // Count people by price category
        String finalRateId = request.getReservationData().getReservations().get(0).getRateId();
        int totalCustomers = 0;
        HashMap<String, Integer> counterMap = new HashMap<>();
        for (Reservation reservation : request.getReservationData().getReservations()) {
            for (Passenger passenger : reservation.getPassengers()) {
                totalCustomers++;
                String pricingCategoryId = passenger.getPricingCategoryId();
                counterMap.put(pricingCategoryId, counterMap.getOrDefault(pricingCategoryId, 0) + 1);
            }
        }
        tourAvailableParams.putAll(counterMap);
        try {
            // Step 1. Check tour available
            AppLogger.info(TAG, String.format("TourCMS - tourAvailableResponse %s", tourAvailableParams));
            String tourAvailableResponse = tourCmsClient.checkTourAvailability(tourAvailableParams);
            // AppLogger.info(TAG, String.format("TourCMS - tourAvailableResponse %s - JSON: %s", tourAvailableParams, Mapping.MAPPER.writeValueAsString(Mapping.MAPPER.readTree(tourAvailableResponse))));
            JsonNode components = Mapping.MAPPER.readTree(tourAvailableResponse).path("available_components").path("component");
            if (components.isMissingNode() || !components.elements().hasNext()) {
                AppLogger.warn(TAG, "Components is missing OR do not has next!");
                successfulReservation.setReservationConfirmationCode(null);
                response.setSuccessfulReservation(successfulReservation);
                exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
                exchange.getResponseSender().send(new Gson().toJson(response));
                return;
            }

            List<JsonNode> componentsList = components.isArray() ?
                    ImmutableList.copyOf(components) :
                    ImmutableList.of(components);

            JsonNode foundComponent = componentsList.stream()
                    .filter(c -> c.get("supplier_note").asText().equals(finalRateId))
                    .findFirst()
                    .orElseGet(() -> componentsList.isEmpty() ? null : componentsList.get(0));

            if (foundComponent == null) {
                AppLogger.warn(TAG, "Component is NULL!");
                successfulReservation.setReservationConfirmationCode(null);
                response.setSuccessfulReservation(successfulReservation);
                exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
                exchange.getResponseSender().send(new Gson().toJson(response));
                return;
            }

            String componentKey = foundComponent.path("component_key").asText();
            AppLogger.info(TAG, String.format("Found component: %s", componentKey));

            // Step 2. Create tm
            TourCMSBooking tourCMSBooking = new TourCMSBooking();
            tourCMSBooking.setTotalCustomers(totalCustomers);
            // booking.setBookingKey("");

            TourCMSComponent tourCMSComponent = new TourCMSComponent();
            tourCMSComponent.setComponentKey(componentKey);

            TourCMSComponents tourCMSComponents = new TourCMSComponents();
            tourCMSComponents.setComponentList(Collections.singletonList(tourCMSComponent));
            tourCMSBooking.setComponents(tourCMSComponents);

            TourCMSCustomer customer = new TourCMSCustomer();
//            String[] names = Mapping.splitFullName(request.getReservationData().getBookingSource().getExtranetUser().getFullName());
//            customer.setEmail(request.getReservationData().getBookingSource().getExtranetUser().getEmail());
//            customer.setFirstName(names[0]);
//            customer.setSurname(names[1]);
            customer.setEmail(request.getReservationData().getCustomerContact().getEmail());
            customer.setFirstName(request.getReservationData().getCustomerContact().getFirstName());
            customer.setSurname(request.getReservationData().getCustomerContact().getLastName());
//            customer.setAddress(request.getReservationData().getCustomerContact().getAddress());
//            customer.setCountry(request.getReservationData().getCustomerContact().getCountry());
//            customer.setPostcode(request.getReservationData().getCustomerContact().getPostCode());
            customer.setTelMobile(request.getReservationData().getCustomerContact().getPhone());
//            customer.setPassportNumber(request.getReservationData().getCustomerContact().getPassportNumber());
//            DateYMD passportExpiry = request.getReservationData().getCustomerContact().getPassportExpiry();
//            String passport = String.format("%04d-%02d-%02d", passportExpiry.getYear(), passportExpiry.getMonth(), passportExpiry.getDay());
//            customer.setPassportExpiryDate(passport);

            TourCMSCustomers customers = new TourCMSCustomers();
            customers.setCustomerList(Collections.singletonList(customer));
            tourCMSBooking.setCustomers(customers);
            String temporaryBookingResponse = tourCmsClient.createTemporaryBooking(tourCMSBooking);
            JsonNode bookingNode = Mapping.MAPPER.readTree(temporaryBookingResponse).path("booking");
            if (bookingNode.isMissingNode() || !bookingNode.elements().hasNext()) {
                AppLogger.warn(TAG, "Booking is missing OR do not has next!");
                successfulReservation.setReservationConfirmationCode(null);
                response.setSuccessfulReservation(successfulReservation);
                exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
                exchange.getResponseSender().send(new Gson().toJson(response));
                return;
            }

            String bookingId = bookingNode.path("booking_id").asText();
            if (bookingId == null || bookingId.isEmpty()) {
                AppLogger.warn(TAG, "Booking ID is NULL OR Empty!");
                successfulReservation.setReservationConfirmationCode(null);
                response.setSuccessfulReservation(successfulReservation);
                exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
                exchange.getResponseSender().send(new Gson().toJson(response));
                return;
            }

            successfulReservation.setReservationConfirmationCode(bookingId);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException | JAXBException e) {
            AppLogger.error(TAG, String.format("Couldn't check tour availability: %s", e.getMessage()), e);
            successfulReservation.setReservationConfirmationCode(null);
            response.setSuccessfulReservation(successfulReservation);
            exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(new Gson().toJson(response));
            return;
        }

        response.setSuccessfulReservation(successfulReservation);
        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        String responseJson = new Gson().toJson(response);
        AppLogger.info(TAG, String.format("-> Response: %s", responseJson));
        exchange.getResponseSender().send(responseJson);
    }

    public void cancelReservation(HttpServerExchange exchange) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        CancelReservationRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), CancelReservationRequest.class);
        AppLogger.info(TAG, String.format("Cancel reservation: %s - %s", request.getAgentCode(), request.getReservationConfirmationCode()));
        String requestJson = new Gson().toJson(request);
        AppLogger.info(TAG, String.format("- Request: %s", requestJson));

        Configuration configuration = Configuration.fromRestParameters(request.getParameters());
        TourCmsClient tourCmsClient = new TourCmsClient(configuration.marketplaceId, configuration.channelId, configuration.getTourcmsPrivateKey());

        tourCmsClient.deleteTemporaryBooking(request.getReservationConfirmationCode());

        CancelReservationResponse response = new CancelReservationResponse();
        SuccessfulReservationCancellation greatSuccess = new SuccessfulReservationCancellation();
        response.setSuccessfulReservationCancellation(greatSuccess);

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        String responseJson = new Gson().toJson(response);
        AppLogger.info(TAG, String.format("-> Response: %s", responseJson));
        exchange.getResponseSender().send(responseJson);
    }

    public void confirmBooking(HttpServerExchange exchange) {
        AppLogger.info(TAG, "Confirm booking");
        ConfirmBookingRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), ConfirmBookingRequest.class);
        String requestJson = new Gson().toJson(request);
        AppLogger.info(TAG, String.format("- Request: %s", requestJson));

        Configuration configuration = Configuration.fromRestParameters(request.getParameters());
        TourCmsClient tourCmsClient = new TourCmsClient(configuration.marketplaceId, configuration.channelId, configuration.getTourcmsPrivateKey());

        String date = String.format("%04d-%02d-%02d", request.getReservationData().getDate().getYear(), request.getReservationData().getDate().getMonth(), request.getReservationData().getDate().getDay());
        String startTime = String.format("%02d:%02d", request.getReservationData().getTime().getHour(), request.getReservationData().getTime().getMinute());

        ConfirmBookingResponse response = new ConfirmBookingResponse();
        processBookingSourceInfo(request.getReservationData().getBookingSource());

        TourCMSBooking booking = new TourCMSBooking();
        booking.setBookingId(request.getReservationConfirmationCode());
        booking.setSuppressEmail(1); // Ignore send email to customer from TourCMS

        try {
            String commitBookingResponse = tourCmsClient.commitBooking(booking);
            String bookingId = Mapping.MAPPER.readTree(commitBookingResponse).path("booking").path("booking_id").asText();
            String barcodeData = Mapping.MAPPER.readTree(commitBookingResponse).path("booking").path("barcode_data").asText();
            String voucherUrl = Mapping.MAPPER.readTree(commitBookingResponse).path("booking").path("voucher_url").asText();
            if (bookingId == null || bookingId.isEmpty()) {
                AppLogger.warn(TAG, "Booking ID is NULL OR Empty!");
                exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
                exchange.getResponseSender().send(new Gson().toJson(response));
                return;
            }

            SuccessfulBooking successfulBooking = new SuccessfulBooking();
            successfulBooking.setBookingConfirmationCode(bookingId);
            Ticket ticket = new Ticket();
            QrTicket qrTicket = new QrTicket();
            qrTicket.setTicketBarcode(barcodeData);
            ticket.setQrTicket(qrTicket);
            successfulBooking.setBookingTicket(ticket);
            response.setSuccessfulBooking(successfulBooking);

            exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
            String responseJson = new Gson().toJson(response);
            AppLogger.info(TAG, String.format("-> Response: %s", responseJson));
            exchange.getResponseSender().send(responseJson);

            // Send notification
            AppLogger.info(TAG, String.format("Sending email to customer: %s", request.getReservationData().getCustomerContact().getEmail()));
            EmailSender sender = new EmailSender(configuration.smtpServer, configuration.smtpUsername, configuration.smtpPassword, configuration.mailCc);
            sender.sendEmailWithAttachment(
                    request.getReservationData().getCustomerContact().getEmail(),
                    "Booking Confirmation",
                    "Your booking has been confirmed successfully! Click the link below to view your voucher.",
                    request.getReservationData().getCustomerContact().getFirstName() + " " + request.getReservationData().getCustomerContact().getLastName(),
                    bookingId,
                    date,
                    startTime,
                    voucherUrl
            );
            AppLogger.info(TAG, "Sending booking success info to telegram");
            BookingSuccessMessage bookingSuccessMessage = new BookingSuccessMessage(request, commitBookingResponse);
            TelegramClient.sendTelegramMessage(bookingSuccessMessage.toString());
        } catch (JAXBException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            AppLogger.error(TAG, String.format("Couldn't commit booking: %s", e.getMessage()), e);
            exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(new Gson().toJson(response));
        }
    }

    public void amendBooking(HttpServerExchange exchange) {
        AppLogger.info(TAG, "Emend booking");
        AmendBookingRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), AmendBookingRequest.class);
        String requestJson = new Gson().toJson(request);
        AppLogger.info(TAG, String.format("- Request: %s", requestJson));

        Configuration configuration = Configuration.fromRestParameters(request.getParameters());

        // At this point you might want to call your external system to do the actual amendment and return data back.
        // Code below just provides some mocks.

        processBookingSourceInfo(request.getReservationData().getBookingSource());

        AmendBookingResponse response = new AmendBookingResponse();
        SuccessfulAmendment successfulAmendment = new SuccessfulAmendment();
        Ticket ticket = new Ticket();
        QrTicket qrTicket = new QrTicket();
        String ticketBarcode = request.getBookingConfirmationCode() + "_ticket_amended";
        qrTicket.setTicketBarcode(ticketBarcode);
        ticket.setQrTicket(qrTicket);
        successfulAmendment.setBookingTicket(ticket);
        successfulAmendment.setAmendmentConfirmationCode(ticketBarcode);
        response.setSuccessfulAmendment(successfulAmendment);

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(response));
        AppLogger.info(TAG, "Out ::amendBooking");
    }

    /**
     * Example code to get info about the booking initiator.
     * Here you can see which data is available in each bookingSource.getSegment() case
     *
     * @param bookingSource bookinSource data structure that is provided in booking requests
     */
    void processBookingSourceInfo(BookingSource bookingSource) {
        AppLogger.info(TAG, String.format("Sales segment: %s", bookingSource.getSegment().name()));
        AppLogger.info(TAG, String.format("Booking channel: %s %s", bookingSource.getBookingChannel().getId(), bookingSource.getBookingChannel().getTitle()));
        switch (bookingSource.getSegment()) {
            case OTA:
                AppLogger.info(TAG, String.format("OTA system: %s", bookingSource.getBookingChannel().getSystemType()));
                break;
            case MARKETPLACE:
                AppLogger.info(TAG, String.format("Reseller vendor: %s '%s' reg.no. %s", bookingSource.getMarketplaceVendor().getId(), bookingSource.getMarketplaceVendor().getTitle(), bookingSource.getMarketplaceVendor().getCompanyRegistrationNumber()));
                break;
            case AGENT_AREA:
                AppLogger.info(TAG, String.format("Booking agent: %s '%s' reg.no. %s", bookingSource.getBookingAgent().getId(), bookingSource.getBookingAgent().getTitle(), bookingSource.getBookingAgent().getCompanyRegistrationNumber()));
                break;
            case DIRECT_OFFLINE:
                AppLogger.info(TAG, String.format("Extranet user: %s '%s'", bookingSource.getExtranetUser().getEmail(), bookingSource.getExtranetUser().getFullName()));
                break;
        }
    }

    public void createAndConfirmBooking(HttpServerExchange exchange) {
        AppLogger.info(TAG, "In ::createAndConfirmBooking");          // should never happen
//        throw new UnsupportedOperationException();

        CreateConfirmBookingRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), CreateConfirmBookingRequest.class);
        Configuration configuration = Configuration.fromRestParameters(request.getParameters());

        // At this point you might want to call your external system to do the actual reserve&confirm and return data back.
        // Code below just provides some mocks.

        processBookingSourceInfo(request.getReservationData().getBookingSource());
        String confirmationCode = UUID.randomUUID().toString();

        ConfirmBookingResponse response = new ConfirmBookingResponse();
        SuccessfulBooking successfulBooking = new SuccessfulBooking();
        successfulBooking.setBookingConfirmationCode(confirmationCode);
        Ticket ticket = new Ticket();
        QrTicket qrTicket = new QrTicket();
        qrTicket.setTicketBarcode(confirmationCode + "_ticket");
        ticket.setQrTicket(qrTicket);
        successfulBooking.setBookingTicket(ticket);
        response.setSuccessfulBooking(successfulBooking);

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(response));
        AppLogger.info(TAG, "Out ::createAndConfirmBooking");
    }

    /**
     * Once booked, a booking may be cancelled using booking ref number.
     * If your system does not support booking cancellation, one of the current workarounds is to create a cancellation policy (on the Bokun end)
     * which offers no refund. Then a cancellation does not have any monetary effect.
     */
    public void cancelBooking(HttpServerExchange exchange) throws JAXBException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        AppLogger.info(TAG, "Cancel booking");
        CancelBookingRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), CancelBookingRequest.class);
        String requestJson = new Gson().toJson(request);
        AppLogger.info(TAG, String.format("- Request: %s", requestJson));

        Configuration configuration = Configuration.fromRestParameters(request.getParameters());
        TourCmsClient tourCmsClient = new TourCmsClient(configuration.marketplaceId, configuration.channelId, configuration.getTourcmsPrivateKey());

        TourCMSBooking booking = new TourCMSBooking();
        booking.setBookingId(request.getBookingConfirmationCode());
        booking.setNote("Cancel booking by BóKun!");
        booking.setCancelReason("23");

        tourCmsClient.cancelBooking(booking);

        CancelBookingResponse response = new CancelBookingResponse();
        response.setSuccessfulCancellation(new SuccessfulCancellation());

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        String responseJson = new Gson().toJson(response);
        AppLogger.info(TAG, String.format("-> Response: %s", responseJson));
        exchange.getResponseSender().send(responseJson);

        AppLogger.info(TAG, "Sending booking cancel info to telegram");
        BookingCancelMessage bookingCancelMessage = new BookingCancelMessage(booking);
        TelegramClient.sendTelegramMessage(bookingCancelMessage.toString());
    }
}
