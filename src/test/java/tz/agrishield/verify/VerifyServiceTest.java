package tz.agrishield.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tz.agrishield.anomaly.AnomalyService;
import tz.agrishield.security.AuditLedger;
import tz.agrishield.security.RateLimiter;
import tz.agrishield.serial.SerialRepository;
import tz.agrishield.serial.model.UnitSerial;

class VerifyServiceTest {

    @Test
    @DisplayName("Rate-limited requests return RATE_LIMITED without DB access")
    void rateLimited_shortCircuits() {
        StubSerialRepository serialRepo = new StubSerialRepository();
        StubRateLimiter rateLimiter = new StubRateLimiter(true);

        VerifyService verifyService = new VerifyService(
            serialRepo,
            new StubVelocityGuard(VelocityGuard.VelocityResult.OK),
            rateLimiter,
            new StubAnomalyService(),
            new StubAuditLedger()
        );

        VerifyContext ctx = VerifyContext.builder().channel("app").sourceId("dev-1").build();
        VerifyResult result = verifyService.verify("AGR-AAA111", ctx);

        assertEquals(VerifyResult.Outcome.RATE_LIMITED, result.getOutcome());
        assertEquals(false, serialRepo.findByCodeCalled);
    }

    @Test
    @DisplayName("Unknown serial returns NOT_FOUND")
    void notFound_returnsNotFound() {
        StubSerialRepository serialRepo = new StubSerialRepository();
        serialRepo.serialToReturn = Optional.empty();

        VerifyService verifyService = new VerifyService(
            serialRepo,
            new StubVelocityGuard(VelocityGuard.VelocityResult.OK),
            new StubRateLimiter(false),
            new StubAnomalyService(),
            new StubAuditLedger()
        );

        VerifyContext ctx = VerifyContext.builder().channel("app").sourceId("dev-1").build();
        VerifyResult result = verifyService.verify("AGR-UNKNOWN", ctx);

        assertEquals(VerifyResult.Outcome.NOT_FOUND, result.getOutcome());
    }

    @Test
    @DisplayName("Active serial is marked used and returns AUTHENTIC")
    void activeSerial_marksUsed_andReturnsAuthentic() {
        UUID serialId = UUID.randomUUID();

        UnitSerial serial = UnitSerial.builder()
            .serialId(serialId)
            .serialCode("AGR-OK1234")
            .status(UnitSerial.SerialStatus.ACTIVE)
            .batch(UnitSerial.BatchInfo.builder()
                .batchCode("BATCH-1")
                .productName("Test Product")
                .manufacturerName("Test Manufacturer")
                .expiryDate(LocalDate.now().plusDays(30))
                .build())
            .build();

        StubSerialRepository serialRepo = new StubSerialRepository();
        serialRepo.serialToReturn = Optional.of(serial);
        serialRepo.markUsedReturn = true;

        VerifyService verifyService = new VerifyService(
            serialRepo,
            new StubVelocityGuard(VelocityGuard.VelocityResult.OK),
            new StubRateLimiter(false),
            new StubAnomalyService(),
            new StubAuditLedger()
        );

        VerifyContext ctx = VerifyContext.builder().channel("app").sourceId("dev-1").build();
        VerifyResult result = verifyService.verify("AGR-OK1234", ctx);

        assertEquals(VerifyResult.Outcome.AUTHENTIC, result.getOutcome());
        assertEquals(serialId, serialRepo.lastMarkedSerialId);
    }

    @Test
    @DisplayName("Already used serial returns ALREADY_USED")
    void usedSerial_returnsAlreadyUsed() {
        UnitSerial serial = UnitSerial.builder()
            .serialId(UUID.randomUUID())
            .serialCode("AGR-USED123")
            .status(UnitSerial.SerialStatus.USED)
            .build();

        StubSerialRepository serialRepo = new StubSerialRepository();
        serialRepo.serialToReturn = Optional.of(serial);

        VerifyService verifyService = new VerifyService(
            serialRepo,
            new StubVelocityGuard(VelocityGuard.VelocityResult.OK),
            new StubRateLimiter(false),
            new StubAnomalyService(),
            new StubAuditLedger()
        );

        VerifyContext ctx = VerifyContext.builder().channel("app").sourceId("dev-1").build();
        VerifyResult result = verifyService.verify("AGR-USED123", ctx);

        assertEquals(VerifyResult.Outcome.ALREADY_USED, result.getOutcome());
    }

    @Test
    @DisplayName("Velocity anomaly still returns AUTHENTIC and flags anomaly")
    void velocityAnomaly_flagsButAuthentic() {
        UUID serialId = UUID.randomUUID();
        UnitSerial serial = UnitSerial.builder()
            .serialId(serialId)
            .serialCode("AGR-VEL123")
            .status(UnitSerial.SerialStatus.ACTIVE)
            .lastScanLat(-6.8)
            .lastScanLng(39.2)
            .lastScanAt(Instant.now().minusSeconds(30))
            .batch(UnitSerial.BatchInfo.builder().expiryDate(LocalDate.now().plusDays(30)).build())
            .build();

        StubSerialRepository serialRepo = new StubSerialRepository();
        serialRepo.serialToReturn = Optional.of(serial);
        serialRepo.markUsedReturn = true;

        StubAnomalyService anomalyService = new StubAnomalyService();

        VerifyService verifyService = new VerifyService(
            serialRepo,
            new StubVelocityGuard(new VelocityGuard.VelocityResult(true, 450.0, 1800.0, "impossible speed")),
            new StubRateLimiter(false),
            anomalyService,
            new StubAuditLedger()
        );

        VerifyContext ctx = VerifyContext.builder().channel("app").sourceId("dev-2").lat(-3.3).lng(36.7).build();
        VerifyResult result = verifyService.verify("AGR-VEL123", ctx);

        assertEquals(VerifyResult.Outcome.AUTHENTIC, result.getOutcome());
        assertEquals(true, anomalyService.flagged);
    }

    private static class StubSerialRepository extends SerialRepository {
        Optional<UnitSerial> serialToReturn = Optional.empty();
        boolean markUsedReturn = false;
        boolean findByCodeCalled = false;
        UUID lastMarkedSerialId;

        StubSerialRepository() {
            super();
        }

        @Override
        public Optional<UnitSerial> findByCode(String serialCode) {
            findByCodeCalled = true;
            return serialToReturn;
        }

        @Override
        public boolean markUsed(UUID serialId, String channel, Double lat, Double lng) {
            this.lastMarkedSerialId = serialId;
            return markUsedReturn;
        }
    }

    private static class StubRateLimiter extends RateLimiter {
        private final boolean blocked;

        StubRateLimiter(boolean blocked) {
            super(null);
            this.blocked = blocked;
        }

        @Override
        public boolean isBlocked(String type, String identifier, int[] limit) {
            return blocked;
        }
    }

    private static class StubVelocityGuard extends VelocityGuard {
        private final VelocityResult result;

        StubVelocityGuard(VelocityResult result) {
            this.result = result;
        }

        @Override
        public VelocityResult check(UnitSerial serial, double newLat, double newLng) {
            return result;
        }
    }

    private static class StubAnomalyService extends AnomalyService {
        boolean flagged;

        @Override
        public void flagAsync(UnitSerial serial, String anomalyType, String description, Map<String, Object> context) {
            flagged = true;
        }
    }

    private static class StubAuditLedger extends AuditLedger {
    }
}
