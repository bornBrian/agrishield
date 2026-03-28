  package tz.agrishield.security;
  
  import de.mkammerer.argon2.Argon2;
  import de.mkammerer.argon2.Argon2Factory;
  import jakarta.enterprise.context.ApplicationScoped;
  
  /**
   * Handles password hashing and verification.
   * We use Argon2id — the current gold standard.
   *
   * Parameters explained:
   * - iterations: 3 — number of passes over memory
   * - memory: 65536 KB (64MB) — memory required per hash attempt
   *   This makes GPU attacks expensive (each GPU thread needs 64MB)
   * - parallelism: 2 — number of parallel threads per hash
   */
  @ApplicationScoped
  public class PasswordService {
  
      private static final Argon2 ARGON2 = Argon2Factory.createAdvanced(
          Argon2Factory.Argon2Types.ARGON2id
      );
  
      // Call this when a user SETS their password
      public String hash(String plainPassword) {
          // Argon2 handles salt generation internally — unique salt per hash
          return ARGON2.hash(
              3,      // iterations
              65536,  // 64MB memory
              2,      // parallelism
              plainPassword.toCharArray()
          );
          // Result looks like: $argon2id$v=19$m=65536,t=3,p=2$...
          // The salt and parameters are embedded in the hash string
      }
  
      // Call this when a user LOGS IN
      public boolean verify(String plainPassword, String storedHash) {
          // IMPORTANT: char[] is used instead of String for password.
          // String objects are immutable and stay in memory longer.
          // char[] can be explicitly zeroed out after use — better security.
          return ARGON2.verify(storedHash, plainPassword.toCharArray());
      }
  }
