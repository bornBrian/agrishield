package tz.agrishield.verify;

import tz.agrishield.serial.model.UnitSerial;

public class VerifyResult {

    public enum Outcome {
        AUTHENTIC,
        NOT_FOUND,
        ALREADY_USED,
        REVOKED,
        EXPIRED,
        RATE_LIMITED
    }

    private Outcome outcome;
    private UnitSerial.BatchInfo batch;
    private String expiryDate;

    public Outcome getOutcome() { return outcome; }
    public UnitSerial.BatchInfo getBatch() { return batch; }
    public String getExpiryDate() { return expiryDate; }

    public static VerifyResult authentic(UnitSerial serial) {
        VerifyResult result = new VerifyResult();
        result.outcome = Outcome.AUTHENTIC;
        result.batch = serial.getBatch();
        result.expiryDate = serial.getBatch() != null && serial.getBatch().getExpiryDate() != null
            ? serial.getBatch().getExpiryDate().toString()
            : null;
        return result;
    }

    public static VerifyResult notFound() {
        VerifyResult result = new VerifyResult();
        result.outcome = Outcome.NOT_FOUND;
        return result;
    }

    public static VerifyResult alreadyUsed() {
        VerifyResult result = new VerifyResult();
        result.outcome = Outcome.ALREADY_USED;
        return result;
    }

    public static VerifyResult revoked(UnitSerial.BatchInfo batch) {
        VerifyResult result = new VerifyResult();
        result.outcome = Outcome.REVOKED;
        result.batch = batch;
        return result;
    }

    public static VerifyResult expired(String expiryDate) {
        VerifyResult result = new VerifyResult();
        result.outcome = Outcome.EXPIRED;
        result.expiryDate = expiryDate;
        return result;
    }

    public static VerifyResult rateLimited() {
        VerifyResult result = new VerifyResult();
        result.outcome = Outcome.RATE_LIMITED;
        return result;
    }
}
