package com.ieia.record;

import com.ieia.activity.ActivityType;
import com.ieia.calculation.CalculatedImpact;
import com.ieia.common.BaseEntity;
import com.ieia.organization.Organization;
import com.ieia.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Raw activity data — exactly what the user entered (docs/02-database-schema.md §3). */
@Entity
@Table(name = "environmental_records")
public class EnvironmentalRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_type_id", nullable = false)
    private ActivityType activityType;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(nullable = false, precision = 16, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String unit;

    private String location;

    @Column(name = "source_detail")
    private String sourceDetail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> attributes = new LinkedHashMap<>();

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_by")
    private java.util.UUID createdBy;

    /**
     * Read view of this record's derived impacts. Writes go through
     * {@code CalculatedImpactRepository} in {@code RecordService} (explicit
     * delete + insert) so the unique {@code (record, impact_type)} constraint is
     * never tripped by Hibernate flush ordering. The DB cascades deletes.
     */
    @OneToMany(mappedBy = "record", fetch = FetchType.LAZY)
    @OrderBy("impactType asc")
    private List<CalculatedImpact> impacts = new ArrayList<>();

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public ActivityType getActivityType() { return activityType; }
    public void setActivityType(ActivityType activityType) { this.activityType = activityType; }
    public LocalDate getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(LocalDate periodMonth) { this.periodMonth = periodMonth; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getSourceDetail() { return sourceDetail; }
    public void setSourceDetail(String sourceDetail) { this.sourceDetail = sourceDetail; }
    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public java.util.UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(java.util.UUID createdBy) { this.createdBy = createdBy; }
    public List<CalculatedImpact> getImpacts() { return impacts; }
}
