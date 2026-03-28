  package tz.agrishield.verify;
  
  import tz.agrishield.serial.model.UnitSerial;
  import jakarta.enterprise.context.ApplicationScoped;
  import java.time.Duration;
  import java.time.Instant;
  
  @ApplicationScoped
  public class VelocityGuard {
  
      // If a code is scanned 450km away within 15 minutes, that's 1,800km/h.
      // The fastest road vehicle in Tanzania is maybe 120km/h.
      // Anything faster than that is physically impossible — the code was copied.
      private static final double MAX_SPEED_KMH  = 120.0;
      private static final double GPS_BUFFER_KM  = 5.0;   // buffer for GPS inaccuracy
      private static final double EARTH_RADIUS_KM = 6371.0;
  
      public record VelocityResult(boolean anomaly, double distKm, double speedKmh, String reason) {
          public static final VelocityResult OK = new VelocityResult(false, 0, 0, null);
      }
  
      public VelocityResult check(UnitSerial serial, double newLat, double newLng) {
          // If we have no previous GPS data, nothing to compare against
          if (!serial.hasBeenScannedBefore()) return VelocityResult.OK;
  
          double distKm = haversine(
              serial.getLastScanLat(), serial.getLastScanLng(),
              newLat, newLng
          );
  
          long seconds = Duration.between(serial.getLastScanAt(), Instant.now()).getSeconds();
          if (seconds < 1) seconds = 1; // avoid division by zero
  
          double speedKmh = (distKm / seconds) * 3600.0;
  
          // Only flag if meaningfully far away (not just GPS drift in same location)
          if (distKm > GPS_BUFFER_KM && speedKmh > MAX_SPEED_KMH) {
              String reason = String.format(
                  "%.0fkm travelled in %ds = %.0fkm/h — physically impossible by road",
                  distKm, seconds, speedKmh
              );
              return new VelocityResult(true, distKm, speedKmh, reason);
          }
  
          return VelocityResult.OK;
      }
  
      // ── HAVERSINE FORMULA ─────────────────────────────────────────────
      // Calculates distance in km between two GPS coordinates
      private double haversine(double lat1, double lon1, double lat2, double lon2) {
          double dLat = Math.toRadians(lat2 - lat1);  // difference in latitude
          double dLon = Math.toRadians(lon2 - lon1);  // difference in longitude
  
          double a = Math.pow(Math.sin(dLat / 2), 2)
                   + Math.cos(Math.toRadians(lat1))
                   * Math.cos(Math.toRadians(lat2))
                   * Math.pow(Math.sin(dLon / 2), 2);
  
          double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
          return EARTH_RADIUS_KM * c;
      }
  }
