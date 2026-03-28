package tz.agrishield.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import redis.clients.jedis.JedisPool;
import tz.agrishield.anomaly.AnomalyService;
import tz.agrishield.common.DatabasePool;
import tz.agrishield.common.FlywayMigrator;
import tz.agrishield.security.AuditLedger;
import tz.agrishield.security.RateLimiter;
import tz.agrishield.serial.SerialRepository;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "AGRISHIELD_IT", matches = "true")
class VerifyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("agrishield_test")
        .withUsername("test_user")
        .withPassword("test_pass");

    private VerifyService verifyService;
    private SerialRepository serialRepo;

    @org.junit.jupiter.api.BeforeAll
    void setup() {
        String jdbcUrl = postgres.getJdbcUrl();
        DatabasePool pool = new DatabasePool(jdbcUrl, "test_user", "test_pass");
        FlywayMigrator.migrate(jdbcUrl, "test_user", "test_pass");

        serialRepo = new SerialRepository(pool);
        JedisPool jedis = new JedisPool("localhost", 6379);
        RateLimiter rateLimiter = new RateLimiter(jedis);
        AuditLedger audit = new AuditLedger();
        AnomalyService anomaly = new AnomalyService();
        VelocityGuard velocity = new VelocityGuard();

        verifyService = new VerifyService(serialRepo, velocity, rateLimiter, anomaly, audit);
    }

    @org.junit.jupiter.api.AfterAll
    void tearDown() {
        if (postgres != null && postgres.isRunning()) {
            postgres.close();
        }
    }

    @BeforeEach
    void seedData() {
        serialRepo.deleteTestSerials("AGR-INT-TEST-%");
        serialRepo.insertTestSerial("AGR-INT-TEST-ACTIVE", "BATCH-TEST-001");
    }

    @Test
    @DisplayName("Integration: serial can be verified once")
    void integrationVerify_marksUsed() {
        VerifyContext ctx = VerifyContext.builder().channel("app").sourceId("device-1").build();

        VerifyResult result1 = verifyService.verify("AGR-INT-TEST-ACTIVE", ctx);
        assertEquals(VerifyResult.Outcome.AUTHENTIC, result1.getOutcome());

        VerifyResult result2 = verifyService.verify("AGR-INT-TEST-ACTIVE", ctx);
        assertEquals(VerifyResult.Outcome.ALREADY_USED, result2.getOutcome());
    }
}
