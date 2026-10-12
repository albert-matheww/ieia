package com.ieia.activity.dto;

import com.ieia.activity.ActivityType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ActivityTypeDto(
        UUID id,
        String code,
        String category,
        String name,
        String description,
        String defaultUnit,
        List<String> allowedUnits,
        Map<String, Object> attributeSchema,
        boolean active
) {
    public static ActivityTypeDto from(ActivityType at) {
        return new ActivityTypeDto(
                at.getId(), at.getCode(), at.getCategory().name(), at.getName(), at.getDescription(),
                at.getDefaultUnit(), at.getAllowedUnits(), at.getAttributeSchema(), at.isActive());
    }
}
