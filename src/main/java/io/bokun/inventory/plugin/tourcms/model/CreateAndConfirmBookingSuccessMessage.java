package io.bokun.inventory.plugin.tourcms.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.bokun.inventory.plugin.api.rest.CreateConfirmBookingRequest;
import io.bokun.inventory.plugin.tourcms.util.AppLogger;

import java.util.ArrayList;
import java.util.List;

public class CreateAndConfirmBookingSuccessMessage {

    private static final String TAG = "CreateAndConfirmBookingSuccessMessage";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected CreateConfirmBookingRequest request;
    protected String webhookStatus;

    protected String bookingId;
    protected String bookingUuid;
    protected String channelId;
    protected String accountId;
    protected String status;
    protected String statusText;
    protected String voucherUrl;
    protected String barcodeData;

    protected List<Component> components = new ArrayList<>();

    public static class Component {
        protected String componentId;
        protected String operatorReference;
        protected String linkedComponentId;
        protected String productId;
        protected String productCode;
        protected String dateId;
        protected String dateCode;
        protected String startDate;
        protected String endDate;
        protected String localPayment;
        protected String customerPayment;
        protected String rateBreakdown;
        protected String rateDescription;
        protected String startTime;
        protected String endTime;
        protected String dateType;
        protected String componentName;
        protected String saleQuantityRule;
        protected String saleQuantity;
        protected String voucherLink;
        protected String voucherLabel;
    }

    public CreateAndConfirmBookingSuccessMessage(CreateConfirmBookingRequest request, String json, String webhookStatus) {
        this.request = request;
        this.webhookStatus = webhookStatus;
        try {
            JsonNode root = MAPPER.readTree(json);

            this.bookingId = root.path("booking").path("booking_id").asText();
            this.bookingUuid = root.path("booking").path("booking_uuid").asText();
            this.channelId = root.path("booking").path("channel_id").asText();
            this.accountId = root.path("booking").path("account_id").asText();
            this.status = root.path("booking").path("status").asText();
            this.statusText = root.path("booking").path("status_text").asText();
            this.voucherUrl = root.path("booking").path("voucher_url").asText();
            this.barcodeData = root.path("booking").path("barcode_data").asText();

            JsonNode componentNode = root.path("booking").path("components").path("component");

            if (componentNode.isArray()) {
                for (JsonNode node : componentNode) {
                    components.add(parseComponent(node));
                }
            } else if (componentNode.isObject()) {
                components.add(parseComponent(componentNode));
            }
        } catch (Exception e) {
            AppLogger.error(TAG, "Failed to parse JSON for BookingSuccessMessage", e);
        }
    }

    private Component parseComponent(JsonNode componentNode) {
        Component component = new Component();
        component.componentId = componentNode.path("component_id").asText();
        component.operatorReference = componentNode.path("operator_reference").asText();
        component.linkedComponentId = componentNode.path("linked_component_id").asText();
        component.productId = componentNode.path("product_id").asText();
        component.productCode = componentNode.path("product_code").asText();
        component.dateId = componentNode.path("date_id").asText();
        component.dateCode = componentNode.path("date_code").asText();
        component.startDate = componentNode.path("start_date").asText();
        component.endDate = componentNode.path("end_date").asText();
        component.localPayment = componentNode.path("local_payment").asText();
        component.customerPayment = componentNode.path("customer_payment").asText();
        component.rateBreakdown = componentNode.path("rate_breakdown").asText();
        component.rateDescription = componentNode.path("rate_description").asText();
        component.startTime = componentNode.path("start_time").asText();
        component.endTime = componentNode.path("end_time").asText();
        component.dateType = componentNode.path("date_type").asText();
        component.componentName = componentNode.path("component_name").asText();
        component.saleQuantityRule = componentNode.path("sale_quantity_rule").asText();
        component.saleQuantity = componentNode.path("sale_quantity").asText();

        JsonNode urlsNode = componentNode.path("urls").path("url");
        component.voucherLink = urlsNode.path("link").asText();
        component.voucherLabel = urlsNode.path("label").asText();

        return component;
    }

    // Escape MarkdownV2 characters
    protected String escapeMarkdownV2(String text) {
        if (text != null) {
            return text.replaceAll("([_*\\[\\]()~`>#+=|{}.!-])", "\\\\$1");
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder body = new StringBuilder();
        body.append("*\\[BOOKING SUCCESS\\]* ✅\n");

        body.append("\\- *Booking Id*: `").append(escapeMarkdownV2(bookingId)).append("`\n");
        body.append("\\- *Channel Id*: `").append(escapeMarkdownV2(channelId)).append("`\n");
        body.append("\\- *Account Id*: `").append(escapeMarkdownV2(accountId)).append("`\n");
        body.append("\\- *Status*: ").append(escapeMarkdownV2(statusText)).append("\n");
        body.append("\\- *Voucher URL*: [Link](").append(escapeMarkdownV2(voucherUrl)).append(")\n");

        body.append("\n*Customer Contact Information:*\n");
        body.append("\\- *First Name*: ").append(escapeMarkdownV2(request.getReservationData().getCustomerContact().getFirstName())).append("\n");
        body.append("\\- *Last Name*: ").append(escapeMarkdownV2(request.getReservationData().getCustomerContact().getLastName())).append("\n");
        body.append("\\- *Email*: `").append(escapeMarkdownV2(request.getReservationData().getCustomerContact().getEmail())).append("`\n");
        body.append("\\- *Phone*: `").append(escapeMarkdownV2(request.getReservationData().getCustomerContact().getPhone())).append("`\n");

        for (Component component : components) {
            body.append("\n*Component Information:*\n");
            body.append("\\- *Component Name*: ").append(escapeMarkdownV2(component.componentName)).append("\n");
            body.append("\\- *Product Id*: ").append(escapeMarkdownV2(component.productId)).append("\n");
            body.append("\\- *Start Date*: ").append(escapeMarkdownV2(component.startDate)).append(" at ").append(escapeMarkdownV2(component.startTime)).append("\n");
            body.append("\\- *End Date*: ").append(escapeMarkdownV2(component.endDate)).append(" at ").append(escapeMarkdownV2(component.endTime)).append("\n");
            body.append("\\- *Sale Quantity*: ").append(escapeMarkdownV2(component.saleQuantity)).append("\n");
            body.append("\\- *Rate Description*: ").append(escapeMarkdownV2(component.rateDescription)).append("\n");

//            if (component.voucherLink != null && !component.voucherLink.isEmpty()) {
//                body.append("\\- *Voucher*: [").append(escapeMarkdownV2(component.voucherLabel)).append("](").append(escapeMarkdownV2(component.voucherLink)).append(")\n");
//            }
        }

        body.append("\n *WEBHOOK STATUS*: ").append(escapeMarkdownV2(webhookStatus)).append("\n");

        return body.toString();
    }
}
