package tz.agrishield.verify;

import java.util.HashMap;
import java.util.Map;

public class VerifyContext {

    private String channel;
    private String sourceId;
    private Double lat;
    private Double lng;

    public String getChannel() { return channel; }
    public String getSourceId() { return sourceId; }
    public Double getLat() { return lat; }
    public Double getLng() { return lng; }

    public boolean hasGps() {
        return lat != null && lng != null;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("channel", channel);
        map.put("sourceId", sourceId);
        map.put("lat", lat);
        map.put("lng", lng);
        return map;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final VerifyContext value = new VerifyContext();

        public Builder channel(String channel) { value.channel = channel; return this; }
        public Builder sourceId(String sourceId) { value.sourceId = sourceId; return this; }
        public Builder lat(Double lat) { value.lat = lat; return this; }
        public Builder lng(Double lng) { value.lng = lng; return this; }

        public VerifyContext build() { return value; }
    }
}
