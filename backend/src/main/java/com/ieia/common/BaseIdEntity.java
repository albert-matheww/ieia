package com.ieia.common;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.util.UUID;

/**
 * Identity only — for tables that track their own single timestamp
 * ({@code computed_at}, {@code detected_at}, …) rather than the standard
 * {@code created_at}/{@code updated_at} pair carried by {@link BaseEntity}.
 */
@MappedSuperclass
public abstract class BaseIdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public boolean isNew() { return id == null; }
}
