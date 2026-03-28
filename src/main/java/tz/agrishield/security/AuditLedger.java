package tz.agrishield.security;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@ApplicationScoped
public class AuditLedger {

    public void log(String action, String resourceId, Map<String, Object> context) {
        // Placeholder implementation for now.
    }
}
