package com.ieia.record;

import com.ieia.activity.ActivityType;
import com.ieia.common.exception.ApiException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight validation of a record's {@code attributes} against the activity
 * type's {@code attribute_schema} (docs/02-database-schema.md §3). Not a full
 * JSON-Schema validator — enough to keep dynamic-form data sane:
 * <ul>
 *   <li>{@code fixed}   — the attribute is forced to this constant value</li>
 *   <li>{@code options} — if present, the value must be one of these</li>
 *   <li>{@code required}— the attribute must be present and non-blank</li>
 * </ul>
 * Returns the normalised attribute map to persist.
 */
@Component
public class RecordAttributeValidator {

    @SuppressWarnings("unchecked")
    public Map<String, Object> validateAndNormalise(ActivityType activityType, Map<String, Object> input) {
        Map<String, Object> attrs = new LinkedHashMap<>(input == null ? Map.of() : input);
        Map<String, Object> schema = activityType.getAttributeSchema();
        if (schema == null || schema.isEmpty()) {
            return attrs;
        }

        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String key = entry.getKey();
            if (!(entry.getValue() instanceof Map<?, ?> rawSpec)) {
                continue;
            }
            Map<String, Object> spec = (Map<String, Object>) rawSpec;

            if (spec.containsKey("fixed")) {
                attrs.put(key, spec.get("fixed"));
                continue;
            }

            boolean required = Boolean.TRUE.equals(spec.get("required"));
            Object value = attrs.get(key);

            if (value == null || (value instanceof String s && s.isBlank())) {
                if (required) {
                    throw ApiException.unprocessable("attributes." + key + " is required for "
                            + activityType.getCode());
                }
                continue;
            }

            if (spec.get("options") instanceof List<?> options && !options.isEmpty()
                    && options.stream().noneMatch(o -> String.valueOf(o).equalsIgnoreCase(String.valueOf(value)))) {
                throw ApiException.unprocessable("attributes." + key + " must be one of " + options
                        + " (got '" + value + "')");
            }
        }
        return attrs;
    }
}
