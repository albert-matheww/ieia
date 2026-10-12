package com.ieia.activity;

import com.ieia.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reference-data catalogue entry: a kind of activity a user can log
 * (e.g. {@code electricity_grid}, {@code car_petrol}). Seeded by V2; extendable
 * by an {@code ADMIN}.
 */
@Entity
@Table(name = "activity_types")
public class ActivityType extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityCategory category;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "default_unit", nullable = false)
    private String defaultUnit;

    /** Units the user may pick; validated on record entry. Stored as jsonb array. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_units", nullable = false)
    private List<String> allowedUnits = new ArrayList<>();

    /** Dynamic-form hints for the category-specific {@code attributes} of a record. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attribute_schema", nullable = false)
    private Map<String, Object> attributeSchema = new LinkedHashMap<>();

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public boolean allowsUnit(String unit) {
        return allowedUnits.stream().anyMatch(u -> u.equalsIgnoreCase(unit));
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public ActivityCategory getCategory() { return category; }
    public void setCategory(ActivityCategory category) { this.category = category; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDefaultUnit() { return defaultUnit; }
    public void setDefaultUnit(String defaultUnit) { this.defaultUnit = defaultUnit; }
    public List<String> getAllowedUnits() { return allowedUnits; }
    public void setAllowedUnits(List<String> allowedUnits) { this.allowedUnits = allowedUnits; }
    public Map<String, Object> getAttributeSchema() { return attributeSchema; }
    public void setAttributeSchema(Map<String, Object> attributeSchema) { this.attributeSchema = attributeSchema; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
