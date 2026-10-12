package com.ieia.organization;

import com.ieia.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** An organisation that owns users and rolls up their environmental records. */
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String industry;
    private String country;

    @Column(nullable = false)
    private String region = "GLOBAL";

    @Enumerated(EnumType.STRING)
    @Column(name = "size_category", nullable = false)
    private SizeCategory sizeCategory = SizeCategory.INDIVIDUAL;

    private Integer headcount;

    public enum SizeCategory { INDIVIDUAL, SMALL, MEDIUM, LARGE }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public SizeCategory getSizeCategory() { return sizeCategory; }
    public void setSizeCategory(SizeCategory sizeCategory) { this.sizeCategory = sizeCategory; }
    public Integer getHeadcount() { return headcount; }
    public void setHeadcount(Integer headcount) { this.headcount = headcount; }
}
