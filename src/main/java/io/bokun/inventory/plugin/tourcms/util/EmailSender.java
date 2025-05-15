package io.bokun.inventory.plugin.tourcms.util;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Properties;

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

    public EmailSender(String smtpServer, String username, String password) {
        this.smtpServer = smtpServer != null ? smtpServer : SMTP_SERVER;
        this.smtpUsername = username != null ? username : USERNAME;
        this.smtpPassword = password != null ? password : PASSWORD;
    }

    public void sendEmailWithAttachment(String toEmail, String subject, String messageContent,
                                        String customerName, String bookingId, String fileUrl) {
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
            message.setFrom(new InternetAddress(this.smtpUsername));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject);

            // Tạo MimeBodyPart cho nội dung HTML
            MimeBodyPart messageBodyPart = new MimeBodyPart();
            String htmlTemplate = loadTemplate();
            if (htmlTemplate != null) {
                String formattedHtml = htmlTemplate
                        .replace("{{name}}", customerName)
                        .replace("{{content}}", messageContent)
                        .replace("{{booking_id}}", bookingId)
                        .replace("{{booking_date}}", getCurrentDate())
                        .replace("{{logo_cid}}", "logo_cid");
                messageBodyPart.setContent(formattedHtml, "text/html; charset=utf-8");
            } else {
                messageBodyPart.setText(messageContent);
            }

            // Tải file từ URL
            File downloadedFile = downloadFileFromUrl(fileUrl);
            // Tạo MimeBodyPart cho attachment
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.attachFile(downloadedFile);

            // Đính kèm logo
            MimeBodyPart logoPart = new MimeBodyPart();
            DataSource fds = new FileDataSource(getResourceFile(LOGO_PATH));
            logoPart.setDataHandler(new DataHandler(fds));
            logoPart.setHeader("Content-ID", "<logo_cid>");
            logoPart.setDisposition(MimeBodyPart.INLINE);

            // Tạo multipart
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);
            multipart.addBodyPart(attachmentPart);
            multipart.addBodyPart(logoPart);

            // Set multipart vào message
            message.setContent(multipart);

            // Gửi email
            Transport.send(message);

            AppLogger.info(TAG, String.format("Email sent to %s successfully", toEmail));

            // Xóa file sau khi gửi xong
            if (downloadedFile.exists() && downloadedFile.delete()) {
                AppLogger.info(TAG, "Temporary file deleted: " + downloadedFile.getAbsolutePath());
            } else {
                AppLogger.warn(TAG, "Failed to delete temporary file: " + downloadedFile.getAbsolutePath());
            }

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

    // Tải file từ URL
    private static File downloadFileFromUrl(String fileUrl) throws IOException {
        URL url = new URL(fileUrl);
        String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);

        if (!fileName.contains(".")) {
            fileName = fileName + ".pdf";
        }

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

    public static void main(String[] args) {
        EmailSender sender = new EmailSender(null, null, null);
        sender.sendEmailWithAttachment(
                "phuchau1509@gmail.com",
                "Booking Confirmation with Attachment",
                "Your booking has been confirmed successfully! We have attached the travel itinerary.",
                "Jake 2",
                "BK98765411",
                "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"
        );
    }
}
