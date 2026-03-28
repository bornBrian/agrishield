package tz.agrishield.serial;
  
  import tz.agrishield.serial.model.UnitSerial;
  import tz.agrishield.common.DatabasePool;
  import jakarta.enterprise.context.ApplicationScoped;
  import java.sql.*;
  import java.util.UUID;
import java.util.Optional;
  
  @ApplicationScoped
  public class SerialRepository {
  
      private DatabasePool db;

      protected SerialRepository() {
      }
  
      public SerialRepository(DatabasePool db) {
          this.db = db;
      }
  
      // ── FIND BY CODE — The most called method in the system ──────────
      // This runs on every single verification request.
      // The UNIQUE index on serial_code makes this O(1) — instant.
      public Optional<UnitSerial> findByCode(String serialCode) {
          String sql = """
              SELECT
                  s.serial_id, s.batch_id, s.serial_code, s.status,
                  s.used_at, s.used_via,
                  s.last_scan_lat, s.last_scan_lng, s.last_scan_at,
                  s.anomaly_flag, s.created_at,
                  -- Join batch and product info in one query (no N+1 problem)
                  b.batch_code, b.expiry_date, b.manufacture_date,
                  p.product_name,
                  o.org_name AS manufacturer_name
              FROM unit_serials s
              JOIN batches b ON s.batch_id = b.batch_id
              JOIN products p ON b.product_id = p.product_id
              JOIN organisations o ON b.manufacturer_id = o.org_id
              WHERE s.serial_code = ?
              """;
          // The ? is a PreparedStatement parameter — prevents SQL injection
  
          try (Connection conn = db.getConnection();
               PreparedStatement ps = conn.prepareStatement(sql)) {
  
              ps.setString(1, serialCode);  // set the ? parameter safely
              ResultSet rs = ps.executeQuery();
  
              if (!rs.next()) return Optional.empty(); // not found
  
              UnitSerial.BatchInfo batchInfo = UnitSerial.BatchInfo.builder()
                  .batchCode(rs.getString("batch_code"))
                  .expiryDate(rs.getDate("expiry_date").toLocalDate())
                  .manufactureDate(rs.getDate("manufacture_date").toLocalDate())
                  .productName(rs.getString("product_name"))
                  .manufacturerName(rs.getString("manufacturer_name"))
                  .build();
  
              UnitSerial serial = UnitSerial.builder()
                  .serialId((UUID) rs.getObject("serial_id"))
                  .batchId((UUID) rs.getObject("batch_id"))
                  .serialCode(rs.getString("serial_code"))
                  .status(UnitSerial.SerialStatus.valueOf(rs.getString("status").toUpperCase()))
                  .usedAt(rs.getTimestamp("used_at") != null ?
                      rs.getTimestamp("used_at").toInstant() : null)
                  .lastScanLat(rs.getObject("last_scan_lat") != null ?
                      rs.getDouble("last_scan_lat") : null)
                  .lastScanLng(rs.getObject("last_scan_lng") != null ?
                      rs.getDouble("last_scan_lng") : null)
                  .lastScanAt(rs.getTimestamp("last_scan_at") != null ?
                      rs.getTimestamp("last_scan_at").toInstant() : null)
                  .anomalyFlag(rs.getBoolean("anomaly_flag"))
                  .batch(batchInfo)
                  .build();
  
              return Optional.of(serial);
  
          } catch (SQLException e) {
              throw new RuntimeException("Database error in findByCode", e);
          }
      }
  
      // ── MARK AS USED — Atomic operation — the one-time use enforcement ──
      // This must be atomic: check status AND update in one transaction.
      // If two simultaneous requests try to use the same code,
      // only one succeeds — the other gets "rows updated: 0" and is rejected.
      public boolean markUsed(UUID serialId, String channel,
                             Double lat, Double lng) {
          String sql = """
              UPDATE unit_serials
              SET status = 'used',
                  used_at = NOW(),
                  used_via = ?,
                  last_scan_lat = ?,
                  last_scan_lng = ?,
                  last_scan_at = NOW()
              WHERE serial_id = ?
              AND status = 'active'  -- Only update if STILL active (prevents race condition)
              """;
  
          try (Connection conn = db.getConnection();
               PreparedStatement ps = conn.prepareStatement(sql)) {
  
              ps.setString(1, channel);
              ps.setObject(2, lat);   // null is OK — JDBC handles null correctly
              ps.setObject(3, lng);
              ps.setObject(4, serialId);
  
              int rowsUpdated = ps.executeUpdate();
              return rowsUpdated == 1; // true = success, false = already used or not found
  
          } catch (SQLException e) {
              throw new RuntimeException("Database error in markUsed", e);
          }
      }
  
      // ── GENERATE SERIALS FOR APPROVED BATCH ───────────────────────────
      // Called once when a batch is dual-approved.
      // Inserts {quantity} rows into unit_serials, each with a unique code.
      public void generateSerials(UUID batchId, int quantity) {
          String sql = "INSERT INTO unit_serials (serial_id, batch_id, serial_code, status)" +
                       " VALUES (gen_random_uuid(), ?, ?, 'active')";
  
          try (Connection conn = db.getConnection()) {
              conn.setAutoCommit(false); // start transaction
              try (PreparedStatement ps = conn.prepareStatement(sql)) {
                  for (int i = 0; i < quantity; i++) {
                      String code = generateSerialCode(batchId, i);
                      ps.setObject(1, batchId);
                      ps.setString(2, code);
                      ps.addBatch(); // batch insert — much faster than individual inserts
                  }
                  ps.executeBatch(); // insert all at once
                  conn.commit();
              } catch (SQLException e) {
                  conn.rollback();
                  throw e;
              }
          } catch (SQLException e) {
              throw new RuntimeException("Failed to generate serials", e);
          }
      }

      public void insertTestSerial(String serialCode, String batchCode) {
          String sql = """
              INSERT INTO unit_serials (
                  serial_id, batch_id, serial_code, status, created_at
              )
              VALUES (
                  gen_random_uuid(),
                  COALESCE(
                      (SELECT batch_id FROM batches WHERE batch_code = ? LIMIT 1),
                      (SELECT batch_id FROM batches LIMIT 1)
                  ),
                  ?,
                  'active',
                  NOW()
              )
              """;

          try (Connection conn = db.getConnection();
               PreparedStatement ps = conn.prepareStatement(sql)) {
              ps.setString(1, batchCode);
              ps.setString(2, serialCode);
              ps.executeUpdate();
          } catch (SQLException e) {
              throw new RuntimeException("Failed to insert test serial", e);
          }
      }

      public void deleteTestSerials(String likePattern) {
          String sql = "DELETE FROM unit_serials WHERE serial_code LIKE ?";
          try (Connection conn = db.getConnection();
               PreparedStatement ps = conn.prepareStatement(sql)) {
              ps.setString(1, likePattern);
              ps.executeUpdate();
          } catch (SQLException e) {
              throw new RuntimeException("Failed to delete test serials", e);
          }
      }
  
      // ── CODE FORMAT: AGR-{CATEGORY}-{YEAR}-{6-CHAR-HEX} ─────────────
      // Example: AGR-FERT-2025-A3F7B2
      private String generateSerialCode(UUID batchId, int sequence) {
          // Use SecureRandom for cryptographically strong random hex
          java.security.SecureRandom sr = new java.security.SecureRandom();
          byte[] bytes = new byte[4];
          sr.nextBytes(bytes);
          String hex = String.format("%08x", java.nio.ByteBuffer.wrap(bytes).getInt());
          return "AGR-" + hex.substring(0, 6).toUpperCase();
      }
  }
