package tz.agrishield.certificate;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
  
  @ApplicationScoped
  public class CertificateService {
  
      @Inject private QrCodeService qrService;
  
      /**
       * Generate an official TFDA batch certificate PDF.
       * This is the document manufacturers download and
       * can attach to shipments as proof of approval.
       */
      public byte[] generateBatchCertificate(
          String batchCode,
          String productName,
          String manufacturerName,
          String tfda1Name, String tfda2Name,
          String approvalDate,
          String expiryDate,
          int quantity,
          String certNumber
      ) throws Exception {
  
          try (PDDocument doc = new PDDocument()) {
  
              // A4 page
              PDPage page = new PDPage(PDRectangle.A4);
              doc.addPage(page);
  
              // Load standard fonts
              PDFont fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
              PDFont fontNormal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
  
              try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
  
                  float pageWidth = page.getMediaBox().getWidth();   // ~595 points (A4)
                  float pageHeight = page.getMediaBox().getHeight(); // ~842 points (A4)
  
                  // ── HEADER ──────────────────────────────────────────────
                  // Green header bar at top
                  cs.setNonStrokingColor(0f, 0.4f, 0.2f); // RGB: green
                  cs.addRect(0, pageHeight - 80, pageWidth, 80);
                  cs.fill();
  
                  // Title text in white on green
                  cs.setNonStrokingColor(1f, 1f, 1f); // white
                  cs.beginText();
                  cs.setFont(fontBold, 18);
                  cs.newLineAtOffset(40, pageHeight - 45);
                  cs.showText("AGRISHIELD VERIFICATION CERTIFICATE");
                  cs.newLine();
                  cs.setFont(fontNormal, 10);
                  cs.showText("Tanzania Food and Drugs Authority (TFDA)");
                  cs.endText();
  
                  // ── CERTIFICATE NUMBER ────────────────────────────────
                  cs.setNonStrokingColor(0f, 0f, 0f); // black
                  cs.beginText();
                  cs.setFont(fontBold, 12);
                  cs.newLineAtOffset(40, pageHeight - 110);
                  cs.showText("Certificate Number: " + certNumber);
                  cs.endText();
  
                  // ── BATCH DETAILS TABLE ───────────────────────────────
                  float y = pageHeight - 160;
                  drawDetailRow(cs, fontBold, fontNormal, 40, y,
                      "Product Name:", productName); y -= 30;
                  drawDetailRow(cs, fontBold, fontNormal, 40, y,
                      "Manufacturer:", manufacturerName); y -= 30;
                  drawDetailRow(cs, fontBold, fontNormal, 40, y,
                      "Batch Code:", batchCode); y -= 30;
                  drawDetailRow(cs, fontBold, fontNormal, 40, y,
                      "Approved Date:", approvalDate); y -= 30;
                  drawDetailRow(cs, fontBold, fontNormal, 40, y,
                      "Expiry Date:", expiryDate); y -= 30;
                  drawDetailRow(cs, fontBold, fontNormal, 40, y,
                      "Units Certified:", String.valueOf(quantity)); y -= 30;
                  drawDetailRow(cs, fontBold, fontNormal, 40, y,
                      "Approver 1:", tfda1Name + " (TFDA)"); y -= 30;
                  drawDetailRow(cs, fontBold, fontNormal, 40, y,
                      "Approver 2:", tfda2Name + " (TFDA)"); y -= 60;
  
                  // ── QR CODE SECTION ───────────────────────────────────
                  // Embed QR code for the batch certificate itself
                  // (Not a unit serial — this is the batch-level reference)
                  byte[] qrPng = qrService.generateQrPng(batchCode, 150);
                  PDImageXObject qrImage = PDImageXObject.createFromByteArray(
                      doc, qrPng, "qr");
  
                  cs.drawImage(qrImage, 40, y - 150, 150, 150);
  
                  cs.beginText();
                  cs.setFont(fontNormal, 8);
                  cs.newLineAtOffset(40, y - 165);
                  cs.showText("Scan to verify batch certificate");
                  cs.endText();
  
                  // ── SIGNATURE BLOCKS ──────────────────────────────────
                  float sigY = 120;
                  drawSignatureLine(cs, fontNormal, 250, sigY, tfda1Name, "TFDA Officer 1");
                  drawSignatureLine(cs, fontNormal, 450, sigY, tfda2Name, "TFDA Officer 2");
  
                  // ── FOOTER ────────────────────────────────────────────
                  cs.setNonStrokingColor(0f, 0.4f, 0.2f);
                  cs.addRect(0, 0, pageWidth, 40);
                  cs.fill();
  
                  cs.setNonStrokingColor(1f, 1f, 1f);
                  cs.beginText();
                  cs.setFont(fontNormal, 8);
                  cs.newLineAtOffset(40, 15);
                  cs.showText("Verify at: https://verify.agrishield.go.tz | TFDA Hotline: 0800 110 010");
                  cs.endText();
              }
  
              // Convert to bytes
              ByteArrayOutputStream baos = new ByteArrayOutputStream();
              doc.save(baos);
              return baos.toByteArray();
          }
      }
  
      private void drawDetailRow(PDPageContentStream cs, PDFont bold, PDFont normal,
          float x, float y, String label, String value) throws IOException {
          cs.beginText();
          cs.setFont(bold, 11); cs.setNonStrokingColor(0f, 0f, 0f);
          cs.newLineAtOffset(x, y);
          cs.showText(label);
          cs.setFont(normal, 11);
          cs.newLineAtOffset(160, 0);
          cs.showText(value);
          cs.endText();
      }
  
      private void drawSignatureLine(PDPageContentStream cs, PDFont font,
          float x, float y, String name, String title) throws IOException {
          cs.setStrokingColor(0f, 0f, 0f);
          cs.moveTo(x, y); cs.lineTo(x + 140, y); cs.stroke();
          cs.beginText();
          cs.setFont(font, 9); cs.newLineAtOffset(x, y - 12);
          cs.showText(name); cs.endText();
          cs.beginText();
          cs.setFont(font, 8); cs.newLineAtOffset(x, y - 22);
          cs.showText(title); cs.endText();
      }
  }
