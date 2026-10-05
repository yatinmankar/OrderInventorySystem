package com.example.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * One row per committed transaction that touched an @Audited entity.
 * Envers stamps every _aud row with a foreign key to the revision that created it.
 */
@Entity
@RevisionEntity
@Table(name = "revinfo")
public class AuditRevisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    @Column(name = "rev")
    private long rev;

    @RevisionTimestamp
    @Column(name = "revtstmp")
    private long timestamp;

    public long getRev() { return rev; }
    public void setRev(long rev) { this.rev = rev; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
