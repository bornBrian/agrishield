package tz.agrishield.security;
  
  import jakarta.enterprise.context.ApplicationScoped;
  import redis.clients.jedis.JedisPool;
  import redis.clients.jedis.Jedis;
  
  @ApplicationScoped
  public class RateLimiter {
  
      // Limit definitions: (max requests, window in seconds)
      public static final int[] SMS_PER_PHONE    = {5,  3600};  // 5 per hour
      public static final int[] USSD_PER_PHONE   = {10, 3600};  // 10 per hour
      public static final int[] APP_PER_DEVICE   = {30, 60};    // 30 per minute
      public static final int[] SERIAL_PER_DAY   = {50, 86400}; // 50 checks per serial per day
      public static final int[] LOGIN_PER_IP     = {5,  300};   // 5 login attempts per 5 min
  
      // ── HOW IT WORKS ──────────────────────────────────────────────
      // Redis key: "ratelimit:{type}:{identifier}"
      // Value: a counter that auto-expires after the window
      //        INCR adds 1 and returns the new count
      //        EXPIRE sets time-to-live so Redis auto-cleans old counters
  
      private final JedisPool redisPool;
  
      public RateLimiter(JedisPool redisPool) {
          this.redisPool = redisPool;
      }
  
      /**
       * Returns true if this request should be BLOCKED (over limit).
       * Returns false if the request is allowed through.
       *
       * @param type        e.g. "sms", "login", "serial"
       * @param identifier  the phone number, IP address, or device fingerprint
       * @param limit       [maxRequests, windowSeconds]
       */
      public boolean isBlocked(String type, String identifier, int[] limit) {
          String key = "ratelimit:" + type + ":" + identifier;
          try (Jedis jedis = redisPool.getResource()) {
              long count = jedis.incr(key);  // increment counter, returns new value
              if (count == 1) {
                  // First request in this window — set the expiry
                  jedis.expire(key, limit[1]);
              }
              return count > limit[0]; // blocked if over limit
          }
      }
  
      // Convenience methods
      public boolean isSmsBlocked(String phoneNumber) {
          // Hash the phone number before using as Redis key (privacy)
          String hashed = sha256(phoneNumber);
          return isBlocked("sms", hashed, SMS_PER_PHONE);
      }
  
      private String sha256(String input) {
          try {
              java.security.MessageDigest md =
                  java.security.MessageDigest.getInstance("SHA-256");
              byte[] bytes = md.digest(input.getBytes("UTF-8"));
              StringBuilder sb = new StringBuilder();
              for (byte b : bytes) sb.append(String.format("%02x", b));
              return sb.toString();
          } catch (Exception e) { throw new RuntimeException(e); }
      }
  }
