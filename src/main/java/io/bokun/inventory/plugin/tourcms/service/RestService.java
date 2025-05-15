package io.bokun.inventory.plugin.tourcms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.squareup.okhttp.OkHttpClient;
import io.bokun.inventory.plugin.api.rest.*;
import io.bokun.inventory.plugin.tourcms.Configuration;
import io.bokun.inventory.plugin.tourcms.api.TourCmsClient;
import io.bokun.inventory.plugin.tourcms.model.ProductRateMapping;
import io.bokun.inventory.plugin.tourcms.util.AppLogger;
import io.bokun.inventory.plugin.tourcms.util.Mapping;
import io.undertow.server.HttpServerExchange;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.bokun.inventory.plugin.api.rest.PluginCapability.AVAILABILITY;
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
//        definition.getCapabilities().add(RESERVATIONS);
//        definition.getCapabilities().add(RESERVATION_CANCELLATION);
//        definition.getCapabilities().add(AMENDMENT);

        definition.getParameters().add(asStringParameter(Configuration.TOURCMS_ACCOUNT_ID, true));
        definition.getParameters().add(asStringParameter(Configuration.TOURCMS_CHANNEL_ID, true));
        definition.getParameters().add(asStringParameter(Configuration.TOURCMS_PRIVATE_KEY, true));
        definition.getParameters().add(asStringParameter(Configuration.TOURCMS_FILTER_IDS, false));

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(definition));
    }

    public void searchProducts(@Nonnull HttpServerExchange exchange) {
        AppLogger.info(TAG, "Search products!");
        SearchProductRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), SearchProductRequest.class);
        String requestJson = new Gson().toJson(request);
        AppLogger.info(TAG, String.format("- Request: %s", requestJson));

        Configuration configuration = Configuration.fromRestParameters(request.getParameters());
        TourCmsClient tourCmsClient = new TourCmsClient(configuration.marketplaceId, configuration.channelId, configuration.apiKey);

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
        TourCmsClient tourCmsClient = new TourCmsClient(configuration.marketplaceId, configuration.channelId, configuration.apiKey);

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
            ProductRateMapping productRateMapping = Mapping.parseProductRates(tourShowResponse, tourDeparturesResponse);
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
        TourCmsClient tourCmsClient = new TourCmsClient(configuration.marketplaceId, configuration.channelId, configuration.apiKey);

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
        TourCmsClient tourCmsClient = new TourCmsClient(configuration.marketplaceId, configuration.channelId, configuration.apiKey);

        DatePeriod range = request.getRange();
        String productId = request.getProductId();
        String startDateStart = String.format("%04d-%02d-%02d", range.getFrom().getYear(), range.getFrom().getMonth(), range.getFrom().getDay());
        String startDateEnd = String.format("%04d-%02d-%02d", range.getTo().getYear(), range.getTo().getMonth(), range.getTo().getDay());

        Map<String, Object> params = new HashMap<>();
        params.put("id", productId);
        params.put("start_date_start", startDateStart);
        params.put("start_date_end", startDateEnd);

        List<ProductAvailabilityWithRatesResponse> productAvailabilityWithRatesResponses = new ArrayList<>();

        try {
            String tourResponse = tourCmsClient.getTourDepartures(params);
            JsonNode tourNode = Mapping.MAPPER.readTree(tourResponse).path("tour");

            if (!tourNode.isMissingNode()) {
                JsonNode departuresNode = tourNode.path("dates_and_prices").path("departure");

                List<JsonNode> departuresNodeList = departuresNode.isArray() ?
                        ImmutableList.copyOf(departuresNode) :
                        ImmutableList.of(departuresNode);

                for (JsonNode departure : departuresNodeList) {
                    String startDate = departure.path("start_date").asText(null);
                    String startTime = departure.path("start_time").asText(null);
                    int capacity = departure.path("spaces_remaining").asInt(0);

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
                            RateWithPrice mainRate = Mapping.mapRate(mainPriceNode, tourNode.path("sale_currency").asText(), true);

                            ProductAvailabilityWithRatesResponse response = new ProductAvailabilityWithRatesResponse();
                            response.setCapacity(capacity);
                            response.setDate(tourDate);
                            response.setTime(time);
                            response.setRates(Collections.singletonList(mainRate));

                            productAvailabilityWithRatesResponses.add(response);
                        }

                        // Extra Rates
                        JsonNode extraRatesNode = departure.path("extra_rates").path("rate");
                        if (extraRatesNode.isArray()) {
                            for (JsonNode extraRateNode : extraRatesNode) {
                                if (!extraRateNode.isMissingNode()) {
                                    RateWithPrice extraRate = Mapping.mapRate(extraRateNode, tourNode.path("sale_currency").asText(), false);

                                    ProductAvailabilityWithRatesResponse extraResponse = new ProductAvailabilityWithRatesResponse();
                                    extraResponse.setCapacity(capacity);
                                    extraResponse.setDate(tourDate);
                                    extraResponse.setTime(time);
                                    extraResponse.setRates(Collections.singletonList(extraRate));

                                    productAvailabilityWithRatesResponses.add(extraResponse);
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
        productAvailabilityWithRatesResponses.sort(Comparator
                .comparing((ProductAvailabilityWithRatesResponse p) -> p.getDate().getYear())
                .thenComparing(p -> p.getDate().getMonth())
                .thenComparing(p -> p.getDate().getDay())
                .thenComparing(p -> p.getTime().getHour())
                .thenComparing(p -> p.getTime().getMinute())
        );

        // Set header và gửi response
        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        String response = new Gson().toJson(productAvailabilityWithRatesResponses);
        AppLogger.info(TAG, String.format("-> Response: %s", response));
        exchange.getResponseSender().send(response);
    }

    /**
     * This call secures necessary resource(s), such as activity time slot which can later become a booking. The reservation should be held for some
     * limited time, and reverted back to being available if the booking is not confirmed.
     * <p>
     * Only implement this method if {@link PluginCapability#RESERVATIONS} is among capabilities of your {@link PluginDefinition}.
     * Otherwise you are only required to implement {@link #createAndConfirmBooking(HttpServerExchange)} which does both
     * reservation and confirmation, this method can be left empty or non-overridden.
     */
    public void createReservation(HttpServerExchange exchange) {
        // body of this method can be left empty if reserve & confirm is only supported as a single step
        AppLogger.info(TAG, "In ::createReservation");

        // At this point you might want to call your external system to do the actual reservation and return data back.
        // Code below just provides some mocks.

        ReservationResponse response = new ReservationResponse();
        SuccessfulReservation reservation = new SuccessfulReservation();
        reservation.setReservationConfirmationCode(UUID.randomUUID().toString());
        response.setSuccessfulReservation(reservation);

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(response));
        AppLogger.info(TAG, "Out ::createReservation");
    }

    /**
     * This call cancels existing reservation -- if the booking was not yet confirmed.
     * <p>
     * Only implement this method if {@link PluginCapability#RESERVATIONS} and {@link PluginCapability#RESERVATION_CANCELLATION} are among
     * capabilities of your {@link PluginDefinition}.
     */
    public void cancelReservation(HttpServerExchange exchange) {
        AppLogger.info(TAG, "In ::cancelReservation");

        // At this point you might want to call your external system to do the actual reservation and return data back.
        // Code below just provides some mocks.

        CancelReservationResponse response = new CancelReservationResponse();
        SuccessfulReservationCancellation greatSuccess = new SuccessfulReservationCancellation();
        response.setSuccessfulReservationCancellation(greatSuccess);

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(response));
        AppLogger.info(TAG, "Out ::cancelReservation");
    }

    /**
     * Once reserved, proceed with booking. This will be called in case if reservation has succeeded.
     * <p>
     * Only implement this method if {@link PluginCapability#RESERVATIONS} is among capabilities of your {@link PluginDefinition}.
     * Otherwise you are only required to implement {@link #createAndConfirmBooking(HttpServerExchange)} which does both
     * reservation and confirmation, this method can be left empty or non-overridden.
     */
    public void confirmBooking(HttpServerExchange exchange) {
        // body of this method can be left empty if reserve & confirm is only supported as a single step
        AppLogger.info(TAG, "In ::confirmBooking");

        ConfirmBookingRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), ConfirmBookingRequest.class);
        Configuration configuration = Configuration.fromRestParameters(request.getParameters());

        // At this point you might want to call your external system to do the actual confirmation and return data back.
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
        AppLogger.info(TAG, "Out ::confirmBooking");
    }

    public void amendBooking(HttpServerExchange exchange) {
        AppLogger.info(TAG, "In ::amendBooking");

        AmendBookingRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), AmendBookingRequest.class);
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

    /**
     * Only implement this method if {@link PluginCapability#RESERVATIONS} is <b>NOT</b> among capabilities of your {@link PluginDefinition}.
     * Otherwise you are only required to implement both {@link #createReservation(HttpServerExchange)} and {@link
     * #confirmBooking(HttpServerExchange)} separately; this method should remain empty or non-overridden.
     */
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
    public void cancelBooking(HttpServerExchange exchange) {
        AppLogger.info(TAG, "In ::cancelBooking");

        CancelBookingRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), CancelBookingRequest.class);
        Configuration configuration = Configuration.fromRestParameters(request.getParameters());

        // At this point you might want to call your external system to do the actual booking cancellation and return data back.
        // Code below just provides some mocks.

        CancelBookingResponse response = new CancelBookingResponse();
        response.setSuccessfulCancellation(new SuccessfulCancellation());

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(response));
        AppLogger.info(TAG, "Out ::cancelBooking");
    }
}
