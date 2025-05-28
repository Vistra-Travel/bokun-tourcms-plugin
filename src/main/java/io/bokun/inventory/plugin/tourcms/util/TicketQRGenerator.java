package io.bokun.inventory.plugin.tourcms.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class TicketQRGenerator {
    private static final String TAG = "TicketQrGenerator";
    public static final String BASE_FOLDER_PATH = "assets/ticket_qrcode";
    public static final String HEADER = "Vistra Travel";
    private static final String LOGO_PATH = "assets/logo.png";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, Color> COLOR_MAP = new HashMap<String, Color>() {{
        put("Adult", new Color(0, 56, 105));     // Màu xanh đậm
        put("Senior", new Color(0, 87, 146));    // Màu xanh sáng
        put("Children", new Color(0, 120, 215)); // Màu xanh sáng tươi
        put("Infant", new Color(255, 185, 0));   // Màu vàng
        put("Default", new Color(60, 60, 60));   // Màu xám trung tính
    }};

    private static String getCurrentDateDirectory() {
        String currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        return BASE_FOLDER_PATH + File.separator + currentDate;
    }

    private static void createDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                System.out.println("Created directory: " + dirPath);
            } else {
                System.out.println("Failed to create directory: " + dirPath);
            }
        }
    }

    private static void drawQrCode(Graphics2D g, BitMatrix bitMatrix, int qrX, int qrY, int scale) {
        int qrModules = bitMatrix.getWidth();
        for (int yBit = 0; yBit < qrModules; yBit++) {
            for (int xBit = 0; xBit < qrModules; xBit++) {
                if (bitMatrix.get(xBit, yBit)) {
                    int xPixel = qrX + xBit * scale;
                    int yPixel = qrY + yBit * scale;
                    g.fillRect(xPixel, yPixel, scale, scale);
                }
            }
        }
    }

    public static ObjectNode generateTicketQr(
            String data,
            String tourName,
            String fullName,
            String phone,
            String ticketType,
            String outputFileName
    ) throws WriterException, IOException {
        // === CONFIG ===
        int qrMinSize = 640;
        int shadowOffset = 5;
        int moduleCornerRadius = 5;

        Color qrColor = COLOR_MAP.getOrDefault(ticketType, new Color(60, 188, 200));
        Color gradientStart = new Color(240, 240, 240);
        Color gradientEnd = new Color(210, 210, 210);

        Font headerFont = new Font("Roboto", Font.BOLD, 28);
        Font footerFont = new Font("Roboto", Font.PLAIN, 24);
        FontMetrics headerFm = new Canvas().getFontMetrics(headerFont);
        FontMetrics footerFm = new Canvas().getFontMetrics(footerFont);

        String header1 = tourName != null && !tourName.isEmpty() ? tourName : HEADER;
        String header2 = fullName;
        String header3 = phone;
        String footer1 = ticketType;
        String footer2 = data;

        int spacingBetweenElements = 4;
        int spacingHeaderFooterToQR = 15;
        int footerPaddingBottom = 10;
        int headerHeight = headerFm.getHeight() * 3 + spacingBetweenElements * 2;
        int footerHeight = footerFm.getHeight() * 2 + spacingBetweenElements;

        // === QR generation ===
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        BitMatrix bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, 1, 1, hints);
        int qrModules = bitMatrix.getWidth();
        int scale = qrMinSize / qrModules;
        bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, qrModules, qrModules, hints);

        int qrSize = qrModules * scale;
        int imageWidth = qrSize + 60;
        int imageHeight = 20 + headerHeight + spacingHeaderFooterToQR + qrSize + spacingHeaderFooterToQR + footerHeight + footerPaddingBottom;

        BufferedImage finalImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = finalImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // === Gradient Background ===
        GradientPaint gradientPaint = new GradientPaint(0, 0, gradientStart, 0, imageHeight, gradientEnd);
        g.setPaint(gradientPaint);
        g.fillRect(0, 0, imageWidth, imageHeight);

        // === Header ===
        g.setFont(headerFont);
        g.setColor(new Color(0, 102, 204));
        int y = 20 + headerFm.getAscent();
        g.drawString(header1, (imageWidth - headerFm.stringWidth(header1)) / 2, y);
        g.setColor(Color.DARK_GRAY);
        y += headerFm.getHeight() + spacingBetweenElements;
        g.drawString(header2, (imageWidth - headerFm.stringWidth(header2)) / 2, y);
        g.setColor(Color.BLACK);
        y += headerFm.getHeight() + spacingBetweenElements;
        g.drawString(header3, (imageWidth - headerFm.stringWidth(header3)) / 2, y);

        // === QR code ===
        int qrX = (imageWidth - qrSize) / 2;
        int qrY = y + spacingHeaderFooterToQR;

        // Draw shadow
        g.setColor(new Color(0, 0, 0, 40));
        g.fillRoundRect(qrX + shadowOffset, qrY + shadowOffset, qrSize, qrSize, 25, 25);

        // Draw QR modules
        g.setColor(qrColor);
        for (int yBit = 0; yBit < qrModules; yBit++) {
            for (int xBit = 0; xBit < qrModules; xBit++) {
                if (bitMatrix.get(xBit, yBit)) {
                    int xPixel = qrX + xBit * scale;
                    int yPixel = qrY + yBit * scale;
                    g.fillRoundRect(xPixel, yPixel, scale, scale, moduleCornerRadius, moduleCornerRadius);
                }
            }
        }

        // File logoFile = new File(LOGO_PATH);
        // if (logoFile.exists()) {
        //     BufferedImage logo = ImageIO.read(logoFile);
        //     int logoMaxWidth = 100;
        //     int originalWidth = logo.getWidth();
        //     int originalHeight = logo.getHeight();
        //     float aspectRatio = (float) originalHeight / originalWidth;

        //     int logoWidth = Math.min(originalWidth, logoMaxWidth);
        //     int logoHeight = Math.round(logoWidth * aspectRatio);

        //     int logoX = qrX + (qrSize - logoWidth) / 2;
        //     int logoY = qrY + (qrSize - logoHeight) / 2;

        //     // Vẽ nền trắng với opacity
        //     g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f)); // Opacity 80%
        //     g.setColor(Color.WHITE);
        //     g.fillRoundRect(logoX - 8, logoY - 8, logoWidth + 16, logoHeight + 16, 20, 20);

        //     // Reset lại Composite trước khi vẽ logo
        //     g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        //     g.drawImage(logo, logoX, logoY, logoWidth, logoHeight, null);
        // }

        // === Footer ===
        g.setFont(footerFont);
        g.setColor(new Color(204, 0, 58));
        int footerY = qrY + qrSize + spacingHeaderFooterToQR + footerFm.getAscent();
        g.drawString(footer1, (imageWidth - footerFm.stringWidth(footer1)) / 2, footerY);
        g.setColor(Color.BLACK);
        footerY += footerFm.getHeight() + spacingBetweenElements;
        g.drawString(footer2, (imageWidth - footerFm.stringWidth(footer2)) / 2, footerY);
        g.dispose();

        if (outputFileName == null) {
            File tempFile = File.createTempFile(String.format("Ticket_%s_%s", ticketType, data), ".png");
            ImageIO.write(finalImage, "png", tempFile);

            ObjectNode response = objectMapper.createObjectNode();
            response.put("code", "SUCCESS");
            response.put("message", "QR Code with modern design generated (temp file)");
            response.put("path", tempFile.getAbsolutePath());
            return response;
        }

        // === Save to File ===
        String dateDir = getCurrentDateDirectory();
        createDirectory(dateDir);
        Path outputPath = FileSystems.getDefault().getPath(dateDir + "/" + outputFileName + ".png");
        ImageIO.write(finalImage, "png", outputPath.toFile());

        // === Response JSON ===
        ObjectNode response = objectMapper.createObjectNode();
        response.put("code", "SUCCESS");
        response.put("message", String.format("QR Code %s with modern design generated", data));
        response.put("path", outputPath.toString());
        return response;
    }

    public static void main(String[] args) {
        try {
            ObjectNode result = TicketQRGenerator.generateTicketQr(
                    "D11E798F603021790",
                    "Barcelona City Tour Hop On - Hop Off",
                    "Jake Doe",
                    "+84987654321",
                    "Adult",
                    "Adult_QR_D11E798F603021790"
            );

            if (result.get("code").asText().equals("SUCCESS")) {
                System.out.println("✅ QR Code generated successfully:");
                System.out.println("    ➡️ Path: " + result.get("path").asText());
            } else {
                System.out.println("❌ Failed to generate QR Code:");
                System.out.println("    ➡️ Message: " + result.get("message").asText());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
