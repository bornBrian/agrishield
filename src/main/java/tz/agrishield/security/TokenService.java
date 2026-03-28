package tz.agrishield.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
  
  @ApplicationScoped
  public class TokenService {
  
      // ── KEY GENERATION (run once at startup if no active key exists) ──
      public OctetKeyPair generateEd25519KeyPair() throws Exception {
          return new OctetKeyPairGenerator(Curve.Ed25519)
              .keyID(UUID.randomUUID().toString())
              .generate();
          // OctetKeyPair contains both public and private keys
          // Private key is stored encrypted in signing_keys table
          // Public key is distributed to apps and published online
      }
  
      // ── SIGN A VERIFICATION RECEIPT ──────────────────────────────────
      // Called after a successful verification.
      // The receipt proves to the farmer: "this product was verified at this time"
      public String signVerificationReceipt(
          String serialCode,
          String productName,
          String manufacturerName,
          String expiryDate,
          String outcome,
          OctetKeyPair privateKey
      ) throws Exception {
  
          Instant now = Instant.now();
          String jti = UUID.randomUUID().toString(); // unique token ID
  
          // Build the token claims
          JWTClaimsSet claims = new JWTClaimsSet.Builder()
              .jwtID(jti)                              // ← unique ID (prevents replay)
              .issuer("AgriShield-v1")                 // ← who issued this
              .subject(serialCode)                     // ← what this is about
              .issueTime(Date.from(now))              // ← when issued
              .notBeforeTime(Date.from(now))          // ← not valid before now
              .expirationTime(Date.from(now.plusSeconds(60))) // ← EXPIRES in 60s!
              .claim("product", productName)
              .claim("manufacturer", manufacturerName)
              .claim("expiry", expiryDate)
              .claim("outcome", outcome)
              .build();
  
          // Sign with Ed25519
          JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
              .keyID(privateKey.getKeyID())  // tells verifier which key was used
              .type(JOSEObjectType.JWT)
              .build();
  
          SignedJWT jwt = new SignedJWT(header, claims);
          jwt.sign(new Ed25519Signer(privateKey));
  
          // Store jti in Redis so we can revoke individual tokens if needed
          // (optional but adds extra security)
  
          return jwt.serialize(); // returns a compact string: xxxxx.yyyyy.zzzzz
      }
  
      // ── VERIFY A RECEIPT (client-side check) ─────────────────────────
      public boolean verifyReceipt(String jwtString, OctetKeyPair publicKey) {
          try {
              SignedJWT jwt = SignedJWT.parse(jwtString);
              // Check signature
              if (!jwt.verify(new Ed25519Verifier(publicKey))) return false;
              // Check expiry
              if (jwt.getJWTClaimsSet().getExpirationTime().before(new Date())) return false;
              return true;
          } catch (Exception e) {
              return false; // any parse or verification error = invalid
          }
      }
  }
