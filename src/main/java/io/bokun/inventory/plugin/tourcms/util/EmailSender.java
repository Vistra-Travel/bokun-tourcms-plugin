package io.bokun.inventory.plugin.tourcms.util;

import com.fasterxml.jackson.databind.node.ObjectNode;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.util.ByteArrayDataSource;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class EmailSender {

    private static final String TAG = "EmailSender";
    private static final String SMTP_SERVER = System.getenv("SMTP_SERVER") != null ? System.getenv("SMTP_SERVER") : "redbull.mxrouting.net";
    private static final String USERNAME = System.getenv("SMTP_USERNAME") != null ? System.getenv("SMTP_USERNAME") : "tourcms@vistratravel.net";
    private static final String PASSWORD = System.getenv("SMTP_PASSWORD") != null ? System.getenv("SMTP_PASSWORD") : "XGUYf6EDamdSEfMcamp6";
    private static final String TEMPLATE_PATH = "/templates/mail_1.html";
    private static final String LOGO_PATH = "/templates/logo_1.png";

    private final String smtpServer;
    private final String smtpUsername;
    private final String smtpPassword;
    private final String mailCc;

    public EmailSender(String smtpServer, String username, String password, String mailCc) {
        this.smtpServer = smtpServer != null ? smtpServer : SMTP_SERVER;
        this.smtpUsername = username != null ? username : USERNAME;
        this.smtpPassword = password != null ? password : PASSWORD;
        this.mailCc = mailCc;
    }

    public void sendEmailWithAttachment(
            String toEmail,
            String subject,
            String messageContent,
            String componentName,
            String customerName,
            String customerPhone,
            String bookingId,
            String bookingDate,
            String startTime,
            String voucherLink,
            List<Map<String, String>> ticketCodes
    ) {
        Properties properties = new Properties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.host", this.smtpServer);
        properties.put("mail.smtp.port", "587");
        properties.put("mail.smtp.ssl.trust", this.smtpServer);

        // Tạo Session
        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        });

        try {
            // Tạo message
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(this.smtpUsername, "Operation Team"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));

            // Thêm danh sách CC nếu có
            if (mailCc != null && !mailCc.isEmpty()) {
                message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(mailCc));
                AppLogger.info(TAG, "CC to: " + mailCc);
            }

            message.setSubject(subject);

            // Tạo MimeBodyPart cho nội dung HTML
            MimeBodyPart messageBodyPart = new MimeBodyPart();
            String htmlTemplate = loadTemplate();
            if (htmlTemplate != null) {
                String formattedHtml = htmlTemplate
                        .replace("{{name}}", customerName)
                        .replace("{{content}}", messageContent)
                        .replace("{{booking_id}}", bookingId)
                        .replace("{{booking_date}}", bookingDate)
                        .replace("{{start_time}}", startTime)
                        .replace("{{voucher_link}}", voucherLink)
                        .replace("{{logo_cid}}", "logo_cid");
                messageBodyPart.setContent(formattedHtml, "text/html; charset=utf-8");
            } else {
                messageBodyPart.setText(messageContent);
            }

            // Tạo multipart
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);

            // Đính kèm logo
            MimeBodyPart logoPart = new MimeBodyPart();
            DataSource fds = new FileDataSource(getResourceFile(LOGO_PATH));
            logoPart.setDataHandler(new DataHandler(fds));
            logoPart.setHeader("Content-ID", "<logo_cid>");
            logoPart.setDisposition(MimeBodyPart.INLINE);
            multipart.addBodyPart(logoPart);

            // === 📌 **Download QR Codes and Attach to Email** ===
            for (Map<String, String> ticket : ticketCodes) {
                String ticketName = ticket.get("name");
                String ticketCode = ticket.get("code");

                try {
                    ObjectNode qrResponse = TicketQRGenerator.generateTicketQr(ticketCode, componentName, customerName, customerPhone, ticketName, String.format("Ticket_QR_%s_%s", ticketName, ticketCode));
                    if (qrResponse.get("code").asText().equals("SUCCESS")) {
                        String filePath = qrResponse.get("path").asText();
                        File qrFile = new File(filePath);

                        AppLogger.info(TAG, String.format("QR Code generated successfully for ticket: %s", qrFile.getAbsoluteFile()));

                        // Tạo MimeBodyPart cho QR Code
                        MimeBodyPart qrPart = new MimeBodyPart();
                        qrPart.attachFile(qrFile); // Đính kèm file trực tiếp vào email
                        qrPart.setFileName(String.format("Ticket-%s-%s.png", ticketName, ticketCode));
                        multipart.addBodyPart(qrPart);

                        qrFile.deleteOnExit();
                    } else {
                        AppLogger.error(TAG, String.format("Failed to generate QR Code for ticket: %s", ticketName));
                    }
                } catch (Exception e) {
                    AppLogger.error(TAG, String.format("Failed to generate ticket QR: %s", ticketCode), e);

                    try {
                        byte[] qrData = downloadFileAsByteArray(String.format("https://office.palisis.com/pit/bo/public/barcode.png?size=300&qrcontent=%s", ticketCode));
                        AppLogger.info(TAG, String.format("Downloaded QR Code for ticket: %s", ticketName));

                        // Tạo MimeBodyPart cho QR Code
                        MimeBodyPart qrPart = new MimeBodyPart();
                        qrPart.setDataHandler(new DataHandler(new ByteArrayDataSource(qrData, "image/png")));
                        qrPart.setFileName(String.format("Ticket-%s-%s.png", ticketName, ticketCode));
                        multipart.addBodyPart(qrPart);
                    } catch (Exception e1) {
                        AppLogger.error(TAG, String.format("Failed to download QR Code for ticket: %s", ticketCode), e);
                    }
                }
            }

            // Set multipart vào message
            message.setContent(multipart);

            // Gửi email
            Transport.send(message);

            AppLogger.info(TAG, String.format("Email sent to %s successfully", toEmail));

        } catch (Exception e) {
            AppLogger.error(TAG, String.format("Failed to send email to %s", toEmail), e);
        }
    }

    // Hàm đọc file HTML template
    private static String loadTemplate() {
        StringBuilder contentBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(EmailSender.class.getResourceAsStream(TEMPLATE_PATH)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                contentBuilder.append(line).append("\n");
            }
            return contentBuilder.toString();
        } catch (Exception e) {
            AppLogger.error(TAG, String.format("Failed to load email template %s", TEMPLATE_PATH), e);
        }
        return null;
    }

    // Lấy ngày hiện tại
    private static String getCurrentDate() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }

    private static byte[] downloadFileAsByteArray(String fileUrl) throws IOException {
        try (InputStream in = new URL(fileUrl).openStream();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] temp = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(temp)) != -1) {
                buffer.write(temp, 0, bytesRead);
            }
            return buffer.toByteArray();
        }
    }

    // Tải file từ URL
    private static File downloadFileFromUrl(String fileUrl, String fileName) throws IOException {
        URL url = new URL(fileUrl);

        Path tempDir = Files.createTempDirectory("attachments");
        Path tempFile = tempDir.resolve(fileName);

        try (InputStream in = url.openStream()) {
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        AppLogger.info(TAG, String.format("Downloaded file %s", tempFile.toAbsolutePath()));
        return tempFile.toFile();
    }

    // Lấy đường dẫn thực tế của file trong resource
    private static File getResourceFile(String resourcePath) throws IOException {
        InputStream inputStream = EmailSender.class.getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new FileNotFoundException("Resource not found: " + resourcePath);
        }
        File tempFile = Files.createTempFile("logo_", ".png").toFile();
        Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return tempFile;
    }

    // public static void main(String[] args) {
    //     EmailSender sender = new EmailSender(null, null, null, "nyxtung97@gmail.com,ntuanhung6@gmail.com");

    //     Map<String, String> adultTicket = new HashMap<>();
    //     adultTicket.put("name", "Adult");
    //     adultTicket.put("code", "D11E798F603021790");

    //     Map<String, String> childTicket = new HashMap<>();
    //     childTicket.put("name", "Child");
    //     childTicket.put("code", "E21E798F603021791");

    //     List<Map<String, String>> tickets = new ArrayList<>();
    //     tickets.add(adultTicket);
    //     tickets.add(childTicket);

    //     String bookingId = "BK98765411";
    //     String fullName = "Jk Jake";
    //     sender.sendEmailWithAttachment(
    //             "phuchau1509@gmail.com",
    //             String.format("Booking confirmation - Client %s - Booking ID: %s", fullName, bookingId),
    //             "Your booking has been confirmed successfully! Click the link below to view your voucher.",
    //             "Barcelona City Tour Hop On - Hop Off",
    //             fullName,
    //             "+84987654321",
    //             bookingId,
    //             "2025-05-19",
    //             "10:00",
    //             "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
    //             tickets
    //     );
    // }
}
