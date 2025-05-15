package io.bokun.inventory.plugin.tourcms.model;

import com.fasterxml.jackson.databind.ObjectMapper;

public class BookingCancelMessage {

    private static final String TAG = "BookingCancelMessage";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected TourCMSBooking tourCMSBooking;

    public BookingCancelMessage(TourCMSBooking tourCMSBooking) {
        this.tourCMSBooking = tourCMSBooking;
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
        String cancelReason = tourCMSBooking.getCancelReason();
        if (cancelReason.equals("23")) {
            cancelReason += " - Cancelled by agent (via website)";
        }
        return "*\\[BOOKING CANCEL\\]* ❌\n" +
                "\\- *Booking Id*: `" + escapeMarkdownV2(tourCMSBooking.getBookingId()) + "`\n" +
                "\\- *Note*: " + escapeMarkdownV2(tourCMSBooking.getNote()) + "\n" +
                "\\- *Cancel reason*: `" + escapeMarkdownV2(cancelReason) + "`\n";
    }
}
