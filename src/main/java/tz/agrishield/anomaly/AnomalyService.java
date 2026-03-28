package tz.agrishield.anomaly;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import tz.agrishield.serial.model.UnitSerial;

@ApplicationScoped
public class AnomalyService {

    public void flagAsync(UnitSerial serial, String anomalyType, String description, Map<String, Object> context) {
        // Placeholder implementation for now.
    }
}
