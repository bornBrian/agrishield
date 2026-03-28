  package tz.agrishield.security;
  
  import dev.samstevens.totp.code.*;
  import dev.samstevens.totp.secret.*;
  import dev.samstevens.totp.time.*;
  import jakarta.enterprise.context.ApplicationScoped;
  
  @ApplicationScoped
  public class TotpService {
  
      private final SecretGenerator secretGenerator = new DefaultSecretGenerator(64);
      private final TimeProvider timeProvider = new SystemTimeProvider();
      private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
      private final CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
  
      // ── STEP 1: Generate a secret for a new user ──
      // Called when a regulator first enables TOTP.
      // The secret is stored ENCRYPTED in the database.
      // The QR code is shown ONCE to the user — they scan it with Google Authenticator.
      public String generateSecret() {
          return secretGenerator.generate();
          // Returns e.g.: "JBSWY3DPEHPK3PXP" — a base32 encoded random string
      }
  
      // ── STEP 2: Generate the QR code data for setup ──
      // The user scans this QR with Google Authenticator.
      // After scanning, GA shows 6-digit codes every 30 seconds.
      public String buildQrCodeData(String secret, String userEmail) {
          return "otpauth://totp/AgriShield:" + userEmail
              + "?secret=" + secret
              + "&issuer=AgriShield"
              + "&algorithm=SHA1"
              + "&digits=6"
              + "&period=30";
      }
  
      // ── STEP 3: Verify a code the user types during login ──
      // Returns true if the 6-digit code is correct.
      // The verifier checks current period AND one period either side
      // to handle slight clock drift (user enters code just as it expires).
      public boolean verify(String secret, String codeFromUser) {
          return verifier.isValidCode(secret, codeFromUser);
      }
  }
