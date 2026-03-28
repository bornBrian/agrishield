package tz.agrishield.serial.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class UnitSerial {

    private UUID serialId;
    private UUID batchId;
    private String serialCode;
    private SerialStatus status;
    private Instant usedAt;
    private String usedVia;
    private Double lastScanLat;
    private Double lastScanLng;
    private Instant lastScanAt;
    private boolean anomalyFlag;
    private Instant createdAt;
    private BatchInfo batch;

    public UUID getSerialId() { return serialId; }
    public UUID getBatchId() { return batchId; }
    public String getSerialCode() { return serialCode; }
    public SerialStatus getStatus() { return status; }
    public Instant getUsedAt() { return usedAt; }
    public String getUsedVia() { return usedVia; }
    public Double getLastScanLat() { return lastScanLat; }
    public Double getLastScanLng() { return lastScanLng; }
    public Instant getLastScanAt() { return lastScanAt; }
    public boolean isAnomalyFlag() { return anomalyFlag; }
    public Instant getCreatedAt() { return createdAt; }
    public BatchInfo getBatch() { return batch; }

    public boolean isActive() { return status == SerialStatus.ACTIVE; }
    public boolean isUsed() { return status == SerialStatus.USED; }
    public boolean isRevoked() { return status == SerialStatus.REVOKED; }

    public boolean isExpired() {
        if (status == SerialStatus.EXPIRED) {
            return true;
        }
        return batch != null && batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(LocalDate.now());
    }

    public boolean hasBeenScannedBefore() {
        return lastScanLat != null && lastScanLng != null && lastScanAt != null;
    }

    public enum SerialStatus {
        ACTIVE, USED, REVOKED, EXPIRED
    }

    public static class BatchInfo {
        private String batchCode;
        private String productName;
        private String manufacturerName;
        private LocalDate expiryDate;
        private LocalDate manufactureDate;

        public String getBatchCode() { return batchCode; }
        public String getProductName() { return productName; }
        public String getManufacturerName() { return manufacturerName; }
        public LocalDate getExpiryDate() { return expiryDate; }
        public LocalDate getManufactureDate() { return manufactureDate; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final BatchInfo value = new BatchInfo();

            public Builder batchCode(String batchCode) { value.batchCode = batchCode; return this; }
            public Builder productName(String productName) { value.productName = productName; return this; }
            public Builder manufacturerName(String manufacturerName) { value.manufacturerName = manufacturerName; return this; }
            public Builder expiryDate(LocalDate expiryDate) { value.expiryDate = expiryDate; return this; }
            public Builder manufactureDate(LocalDate manufactureDate) { value.manufactureDate = manufactureDate; return this; }
            public BatchInfo build() { return value; }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final UnitSerial value = new UnitSerial();

        public Builder serialId(UUID serialId) { value.serialId = serialId; return this; }
        public Builder batchId(UUID batchId) { value.batchId = batchId; return this; }
        public Builder serialCode(String serialCode) { value.serialCode = serialCode; return this; }
        public Builder status(SerialStatus status) { value.status = status; return this; }
        public Builder usedAt(Instant usedAt) { value.usedAt = usedAt; return this; }
        public Builder usedVia(String usedVia) { value.usedVia = usedVia; return this; }
        public Builder lastScanLat(Double lastScanLat) { value.lastScanLat = lastScanLat; return this; }
        public Builder lastScanLng(Double lastScanLng) { value.lastScanLng = lastScanLng; return this; }
        public Builder lastScanAt(Instant lastScanAt) { value.lastScanAt = lastScanAt; return this; }
        public Builder anomalyFlag(boolean anomalyFlag) { value.anomalyFlag = anomalyFlag; return this; }
        public Builder createdAt(Instant createdAt) { value.createdAt = createdAt; return this; }
        public Builder batch(BatchInfo batch) { value.batch = batch; return this; }

        public UnitSerial build() {
            return value;
        }
    }
}
