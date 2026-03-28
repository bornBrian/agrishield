package tz.agrishield.certificate;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import jakarta.enterprise.context.ApplicationScoped;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
  
  @ApplicationScoped
  public class QrCodeService {
  
      private static final String VERIFY_BASE_URL = "https://verify.agrishield.go.tz?s=";
  
      /**
       * Generate a QR code for a unit serial.
       * Returns the QR code as PNG bytes (can be saved to file or embedded in PDF).
       *
       * @param serialCode  e.g. "AGR-FERT-2025-A3F7B2"
       * @param sizePx      size in pixels (recommended: 300px for labels, 200px for PDFs)
       * @return            PNG image as byte array
       */
      public byte[] generateQrPng(String serialCode, int sizePx) throws Exception {
  
          String url = VERIFY_BASE_URL + serialCode;
  
          // ZXing hints — configure the QR code properties
          Map<EncodeHintType, Object> hints = new HashMap<>();
          hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
          // ERROR_CORRECTION.H = 30% of QR data can be damaged/obscured
          // and it still scans correctly. Good for physical labels that get scratched.
  
          hints.put(EncodeHintType.MARGIN, 1);  // quiet zone (white border around QR)
          hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
  
          // Generate the QR bit matrix
          QRCodeWriter writer = new QRCodeWriter();
          BitMatrix matrix = writer.encode(url, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
  
          // Convert to PNG image
          BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
  
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          ImageIO.write(image, "PNG", baos);
          return baos.toByteArray();
      }
  
      /**
       * Validate that a QR code string has the correct AgriShield format.
       * Used by the app to quickly reject obviously wrong QR codes before
       * making a server request.
       */
      public boolean isValidAgriShieldQr(String qrContent) {
          if (qrContent == null) return false;
          // Must start with our verify URL
          if (!qrContent.startsWith(VERIFY_BASE_URL)) return false;
          // Extract the serial code part
          String serial = qrContent.substring(VERIFY_BASE_URL.length());
          // Serial must match our format: AGR- followed by alphanumeric
          return serial.matches("AGR-[A-Z0-9]{4,24}");
      }
  }
