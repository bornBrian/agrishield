  package tz.agrishield.verify;
  
  import tz.agrishield.serial.SerialRepository;
  import tz.agrishield.serial.model.UnitSerial;
  import tz.agrishield.security.RateLimiter;
  import tz.agrishield.security.AuditLedger;
  import tz.agrishield.anomaly.AnomalyService;
  import jakarta.enterprise.context.ApplicationScoped;
  import jakarta.inject.Inject;
  import java.util.Optional;
  
  @ApplicationScoped
  public class VerifyService {
  
      @Inject private SerialRepository serialRepo;
      @Inject private VelocityGuard velocityGuard;
      @Inject private RateLimiter rateLimiter;
      @Inject private AnomalyService anomalyService;
      @Inject private AuditLedger audit;

      public VerifyService() {
      }

      public VerifyService(SerialRepository serialRepo,
                           VelocityGuard velocityGuard,
                           RateLimiter rateLimiter,
                           AnomalyService anomalyService,
                           AuditLedger audit) {
          this.serialRepo = serialRepo;
          this.velocityGuard = velocityGuard;
          this.rateLimiter = rateLimiter;
          this.anomalyService = anomalyService;
          this.audit = audit;
      }
  
      // ── THE MAIN VERIFICATION METHOD ──────────────────────────────────
      // Called by SmsService, UssdService, and the REST API for the app.
      // Takes the serial code and context (channel, source, GPS).
      // Returns a VerifyResult object — the caller formats it for the channel.
      public VerifyResult verify(String serialCode, VerifyContext ctx) {
  
          // ① RATE LIMITING — first check, before touching the database
          // If over limit, return early. No DB cost for blocked bots.
          if (rateLimiter.isBlocked(ctx.getChannel(), ctx.getSourceId(),
                  getLimit(ctx.getChannel()))) {
              audit.log("VERIFY_RATE_LIMITED", serialCode, ctx.toMap());
              return VerifyResult.rateLimited();
          }
  
          // ② LOOK UP SERIAL
          Optional<UnitSerial> found = serialRepo.findByCode(serialCode);
  
          if (found.isEmpty()) {
              // Log every not-found attempt (helps detect enumeration attacks)
              audit.log("VERIFY_NOT_FOUND", serialCode, ctx.toMap());
              return VerifyResult.notFound();
          }
  
          UnitSerial serial = found.get();
  
          // ③ STATUS CHECKS — order matters! Check revoked before expired before used.
          if (serial.isRevoked()) {
              audit.log("VERIFY_REVOKED", serial.getSerialId().toString(), ctx.toMap());
              return VerifyResult.revoked(serial.getBatch());
          }
  
          if (serial.isExpired()) {
              audit.log("VERIFY_EXPIRED", serial.getSerialId().toString(), ctx.toMap());
              return VerifyResult.expired(serial.getBatch().getExpiryDate().toString());
          }
  
          // ④ ONE-TIME USE CHECK — THE KEY INNOVATION
          // If the code has already been used once, flag it as suspicious.
          // Do NOT reveal whether the original use was authentic —
          // just tell the farmer: this code was already used.
          if (serial.isUsed()) {
              // Flag this as a reuse attempt — possible counterfeit distribution
              anomalyService.flagAsync(serial, "reuse_attempt",
                  "Serial " + serialCode + " was used again after first verification",
                  ctx.toMap());
              audit.log("VERIFY_REUSE_ATTEMPT", serialCode, ctx.toMap());
              return VerifyResult.alreadyUsed();
          }
  
          // ⑤ VELOCITY CHECK (only if GPS coordinates are provided)
          if (ctx.hasGps() && serial.hasBeenScannedBefore()) {
              VelocityGuard.VelocityResult v = velocityGuard.check(
                  serial, ctx.getLat(), ctx.getLng()
              );
              if (v.anomaly()) {
                  // We still return AUTHENTIC to the farmer — benefit of the doubt.
                  // But we flag the anomaly for TFDA review.
                  // False rejection (blocking a legitimate farmer) is worse than
                  // a delayed detection of a counterfeit.
                  anomalyService.flagAsync(serial, "velocity", v.reason(), ctx.toMap());
              }
          }
  
          // ⑥ ALL CHECKS PASSED — MARK AS USED ATOMICALLY
          // markUsed() only updates if status = 'active'.
          // If a race condition occurs (two requests at same millisecond),
          // only one succeeds — the second returns false and is rejected.
          boolean marked = serialRepo.markUsed(
              serial.getSerialId(),
              ctx.getChannel(),
              ctx.getLat(), ctx.getLng()
          );
  
          if (!marked) {
              // Race condition — another request beat us
              audit.log("VERIFY_RACE_CONDITION", serialCode, ctx.toMap());
              return VerifyResult.alreadyUsed();
          }
  
          // ⑦ SUCCESS — log and return result
          audit.log("VERIFY_AUTHENTIC", serial.getSerialId().toString(), ctx.toMap());
          return VerifyResult.authentic(serial);
      }
  
      private int[] getLimit(String channel) {
          return switch (channel) {
              case "sms"  -> RateLimiter.SMS_PER_PHONE;
              case "ussd" -> RateLimiter.USSD_PER_PHONE;
              case "app"  -> RateLimiter.APP_PER_DEVICE;
              default     -> new int[]{100, 60};
          };
      }
  }
