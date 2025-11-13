package com.openride.ticketing.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * QR code generation and encoding utility using ZXing library.
 * 
 * Generates QR codes with high error correction level for robust scanning.
 * Output is base64-encoded PNG image suitable for embedding in JSON responses.
 */
@Slf4j
public class QRCodeUtil {

    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 300;
    private static final String IMAGE_FORMAT = "PNG";

    /**
     * Generate QR code from data string.
     * 
     * @param data the data to encode
     * @param width QR code width in pixels
     * @param height QR code height in pixels
     * @return base64-encoded PNG image
     * @throws RuntimeException if generation fails
     */
    public static String generateQRCode(String data, int width, int height) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }

        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }

        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, width, height, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, IMAGE_FORMAT, outputStream);

            byte[] imageBytes = outputStream.toByteArray();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            log.debug("Generated QR code of size {}x{} with {} bytes", width, height, imageBytes.length);
            return base64Image;

        } catch (WriterException | IOException e) {
            log.error("Failed to generate QR code", e);
            throw new RuntimeException("QR code generation failed", e);
        }
    }

    /**
     * Generate QR code with default dimensions (300x300).
     * 
     * @param data the data to encode
     * @return base64-encoded PNG image
     */
    public static String generateQRCode(String data) {
        return generateQRCode(data, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Decode base64 QR code image to byte array.
     * 
     * @param base64Image the base64-encoded image
     * @return decoded byte array
     */
    public static byte[] decodeQRCodeImage(String base64Image) {
        if (base64Image == null || base64Image.isEmpty()) {
            throw new IllegalArgumentException("Base64 image cannot be null or empty");
        }

        try {
            return Base64.getDecoder().decode(base64Image);
        } catch (IllegalArgumentException e) {
            log.error("Failed to decode base64 QR code image", e);
            throw new RuntimeException("QR code image decoding failed", e);
        }
    }
}
