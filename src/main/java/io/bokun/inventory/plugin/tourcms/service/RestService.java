package io.bokun.inventory.plugin.tourcms.service;

import java.io.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;

import javax.annotation.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.*;
import com.google.gson.*;
import com.google.inject.*;
import com.squareup.okhttp.*;
import io.bokun.inventory.plugin.api.rest.*;
import io.bokun.inventory.plugin.api.rest.Address;
import io.bokun.inventory.plugin.tourcms.Configuration;
import io.bokun.inventory.plugin.tourcms.api.TourCmsClient;
import io.bokun.inventory.plugin.tourcms.util.AppLogger;
import io.undertow.server.*;

import static io.bokun.inventory.plugin.api.rest.PluginCapability.*;
import static io.undertow.util.Headers.*;
import static java.util.concurrent.TimeUnit.*;

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
        boolean isGetRequest = "GET".equalsIgnoreCase(exchange.getRequestMethod().toString());
        TourCmsClient tourCmsClient = new TourCmsClient();

        String data = "";
        Map<String, Object> params = new HashMap<>();
        params.put("per_page", 200);

        if (!isGetRequest) {
            SearchProductRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), SearchProductRequest.class);
            AppLogger.info(TAG, String.format("Search products - Request params: %s", request.getParameters()));
            Configuration configuration = Configuration.fromRestParameters(request.getParameters());
            tourCmsClient = new TourCmsClient(configuration.marketplaceId, configuration.channelId, configuration.apiKey);

            if (configuration.filterIds != null && !configuration.filterIds.isEmpty()) {
                params.put("tour_id", configuration.filterIds);
            }
        }

        List<BasicProductInfo> products = new ArrayList<>();
        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");

        AppLogger.info(TAG, String.format("Start fetching products from TourCMS: %s - %s - %s - %s", tourCmsClient.marketplaceId, tourCmsClient.channelId, tourCmsClient.apiKey, params));
        try {
            data = tourCmsClient.getProducts(params);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException exception) {
            AppLogger.error(TAG, "Couldn't get products", exception);
        }

        if (data == null || data.isEmpty()) {
            AppLogger.info(TAG, String.format("Empty res data: %s", data));
            exchange.getResponseSender().send(new Gson().toJson(products));
            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        int totalProducts = 0;
        try {
            JsonNode dataNode = objectMapper.readTree(data);

            totalProducts = dataNode.get("total_tour_count").asInt();
            JsonNode productsList = dataNode.get("tour");
            for (JsonNode product : productsList) {
                BasicProductInfo basicProductInfo = new BasicProductInfo();
                basicProductInfo.setId(product.get("tour_id").asText());
                basicProductInfo.setName(product.get("tour_name").asText());
                basicProductInfo.setDescription(product.get("shortdesc").asText());

                basicProductInfo.setPricingCategories(new ArrayList<>());
                PricingCategory fromPrice = new PricingCategory();
                fromPrice.setId(product.get("tour_id").asText() + "_" + product.get("from_price").asText());
                fromPrice.setLabel(product.get("from_price_display").asText());
                basicProductInfo.getPricingCategories().add(fromPrice);

                basicProductInfo.setCities(ImmutableList.of(product.get("location").asText()));
                basicProductInfo.setCountries(ImmutableList.of(product.get("country").asText()));

                products.add(basicProductInfo);
            }

        } catch (JsonProcessingException e) {
            AppLogger.error(TAG, "Couldn't process products", e);
        }

        AppLogger.info(TAG, String.format(" - Return: %s products", totalProducts));
        exchange.getResponseSender().send(new Gson().toJson(products));
    }

    public void getProductById(HttpServerExchange exchange) {
        GetProductByIdRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), GetProductByIdRequest.class);

        AppLogger.info(TAG, String.format("Get product by id - Request params: %s", request.getParameters()));

        Configuration configuration = Configuration.fromRestParameters(request.getParameters());
        TourCmsClient tourCmsClient = new TourCmsClient(configuration.marketplaceId, configuration.channelId, configuration.apiKey);

        String id = request.getExternalId();
        if (id == null || id.isEmpty()) {
            String msg = String.format("Empty request external id: %s", id);
            AppLogger.info(TAG, msg);
            exchange.setStatusCode(400);
            exchange.getResponseSender().send("{'message':'" + msg + "'}");
        }

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String productJson = tourCmsClient.getProduct(id);
            JsonNode productNode = objectMapper.readTree(productJson);
            JsonNode product = productNode.get("tour");

            ProductDescription description = new ProductDescription();
            // 1. id
            description.setId(product.get("tour_id").asText());
            // 2. name
            description.setName(product.get("tour_name").asText());
            // 3. description
            description.setDescription(product.get("shortdesc").asText());

            JsonNode ratesNode = product.path("new_booking").path("people_selection").path("rate");
            List<PricingCategory> prices = new ArrayList<>();
            List<Rate> rates = new ArrayList<>();
            if (ratesNode.isArray()) {
                for (JsonNode rateNode : ratesNode) {
                    Rate rate = new Rate();
                    rate.setId(rateNode.path("rate_id").asText());
                    rate.setLabel(rateNode.path("label_1").asText());
                    rates.add(rate);

                    PricingCategory pricesCategory = new PricingCategory();
                    pricesCategory.setId(product.get("tour_id").asText() + "_" + rateNode.get("from_price").asText());
                    pricesCategory.setLabel(rateNode.get("from_price_display").asText());
                    pricesCategory.setMinAge(rateNode.get("agerange_min").asInt());
                    pricesCategory.setMaxAge(rateNode.get("agerange_max").asInt());
                    prices.add(pricesCategory);
                }
            }
            // 3. pricingCategories
            description.setPricingCategories(prices);
            // 4. rates
            description.setRates(rates);

            // 5. bookingType
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
                JsonNode startTime = product.path("start_time");
                if (startTime.isTextual() && startTime.asText().contains(":")) {
                    List<Time> startTimes = new ArrayList<>();
                    String[] timeParts = startTime.asText().split(":");
                    int hour = Integer.parseInt(timeParts[0]);
                    int minute = Integer.parseInt(timeParts[1]);
                    Time time = new Time();
                    time.setHour(hour);
                    time.setMinute(minute);
                    startTimes.add(time);
                    description.setStartTimes(startTimes);
                }
            }

            // 13. ticketType
            JsonNode deliveryFormat = product.path("delivery_formats").path("delivery_format");
            if (!deliveryFormat.isEmpty()) {
                try {
                    TicketType ticketType = TicketType.valueOf(deliveryFormat.asText());
                    description.setTicketType(ticketType);
                } catch (IllegalArgumentException e) {
                    description.setTicketType(TicketType.QR_CODE);
                }
            }

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
            JsonNode alternativeTours = product.path("alternative_tours").path("tour");
            List<Extra> extras = new ArrayList<>();
            if (alternativeTours.isArray()) {
                for (JsonNode item : alternativeTours) {
                    Extra extra = new Extra();
                    extra.setId(item.get("tour_id").asText());
                    extra.setTitle(item.get("tour_name").asText());
                    extra.setDescription(item.get("tour_name_long").asText());
                    extra.setOptional(true);                  // Giả định là tất cả đều optional
                    extra.setMaxPerBooking(1);                // Giả định chỉ đặt 1 lần mỗi booking
                    extra.setLimitByPax(false);               // Không giới hạn theo số người
                    extra.setIncreasesCapacity(false);        // Không tăng số lượng người cho phép
                    extras.add(extra);
                }
            }
            description.setExtras(ImmutableList.copyOf(extras));

            exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(new Gson().toJson(description));
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException exception) {
            AppLogger.error(TAG, "Couldn't get product", exception);
            exchange.setStatusCode(500);
            exchange.getResponseSender().send("{'message':'Could not get product'}");
        }
    }

    /**
     * A set of product ids provided, return their availability over given date range ("shallow" call).
     * This will return a subset of product IDs passed on via ProductAvailabilityRequest.
     * Note: even though request contains capacity and date range, for a matching product it is enough to have availabilities for *some* dates over
     * requested period. Subsequent GetProductAvailability request will clarify precise dates and capacities.
     */
    public void getAvailableProducts(HttpServerExchange exchange) {
        ProductsAvailabilityRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), ProductsAvailabilityRequest.class);
        Configuration configuration = Configuration.fromRestParameters(request.getParameters());

        // At this point you might want to call your external system to do the actual search and return data back.
        // Code below just provides some mocks.

        if (!request.getExternalProductIds().contains("123")) {
            throw new IllegalStateException("Previous call only returned product having id=123");
        }

        ProductsAvailabilityResponse response = new ProductsAvailabilityResponse();
        response.setActualCheckDone(true);
        response.setProductId("123");

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(ImmutableList.of(response)));
    }

    /**
     * Get availability of a particular product over a date range. This request should follow GetAvailableProducts and provide more details on
     * precise dates/times for each product as well as capacity for each date. This call, however, is for a single product only (as opposed to
     * {@link #getAvailableProducts(HttpServerExchange)} which checks many products but only does a basic shallow check.
     */
    public void getProductAvailability(HttpServerExchange exchange) {
        AppLogger.info(TAG, "In ::getProductAvailability");

        ProductAvailabilityRequest request = new Gson().fromJson(new InputStreamReader(exchange.getInputStream()), ProductAvailabilityRequest.class);
        Configuration configuration = Configuration.fromRestParameters(request.getParameters());

        // At this point you might want to call your external system to do the actual search and return data back.
        // Code below just provides some mocks.

        List<ProductAvailabilityWithRatesResponse> l = new ArrayList<>();
        for (int i=0; i<=1; i++) {
            ProductAvailabilityWithRatesResponse response = new ProductAvailabilityWithRatesResponse();
            response.setCapacity(100);

            LocalDate date = LocalDate.now().plusDays(i);
            DateYMD tomorrowDate = new DateYMD();
            tomorrowDate.setYear(date.getYear());
            tomorrowDate.setMonth(date.getMonthValue());
            tomorrowDate.setDay(date.getDayOfMonth());
            response.setDate(tomorrowDate);

            Time tomorrowTime = new Time();
            tomorrowTime.setHour(13);
            tomorrowTime.setMinute(00);
            response.setTime(tomorrowTime);

            RateWithPrice rate = new RateWithPrice();
            rate.setRateId("standard");

            PricePerPerson pricePerPerson = new PricePerPerson();
            pricePerPerson.setPricingCategoryWithPrice(new ArrayList<>());
            {
                PricingCategoryWithPrice adultCategoryPrice = new PricingCategoryWithPrice();
                adultCategoryPrice.setPricingCategoryId("ADT");
                Price adultPrice = new Price();
                adultPrice.setAmount("100");
                adultPrice.setCurrency("EUR");
                adultCategoryPrice.setPrice(adultPrice);
                pricePerPerson.getPricingCategoryWithPrice().add(adultCategoryPrice);
            }
            {
                PricingCategoryWithPrice childCategoryPrice = new PricingCategoryWithPrice();
                childCategoryPrice.setPricingCategoryId("CHD");
                Price childPrice = new Price();
                childPrice.setAmount("10");
                childPrice.setCurrency("EUR");
                childCategoryPrice.setPrice(childPrice);
                pricePerPerson.getPricingCategoryWithPrice().add(childCategoryPrice);
            }
            rate.setPricePerPerson(pricePerPerson);
            response.setRates(ImmutableList.of(rate));
            l.add(response);
        }

        exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(new Gson().toJson(l));
        AppLogger.info(TAG, "Out ::getProductAvailability");
    }

    /**
     * This call secures necessary resource(s), such as activity time slot which can later become a booking. The reservation should be held for some
     * limited time, and reverted back to being available if the booking is not confirmed.
     *
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
     *
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
     *
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
