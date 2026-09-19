package com.uwa.printerfarm.printer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Represents a physical 3D printer in the farm inventory.
 * Mapped to the 'printers' table in PostgreSQL.
 */
@Entity
@Table(name = "printers")
public class Printer {

    @Id
    @Column(length = 50)
    private String id; // e.g. 'PRUSA_XL_1'

    @Column(nullable = false, length = 100)
    private String name; // e.g. 'Prusa XL #1'

    @Column(nullable = false, length = 50)
    private String model; // e.g. 'PRUSA_XL', 'PRUSA_MK4S', 'PRUSA_CORE_ONE'

    @Column(nullable = false, length = 30)
    private String status = "IDLE"; // 'IDLE', 'PRINTING', 'MAINTENANCE', 'OFFLINE'

    @Column(name = "current_material", length = 30)
    private String currentMaterial; // e.g. 'PLA'

    @Column(name = "current_colour", length = 30)
    private String currentColour; // e.g. 'Prusa Orange'

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Printer() {
        // required by JPA
    }

    public Printer(String id, String name, String model, String status,
                   String currentMaterial, String currentColour) {
        this.id = id;
        this.name = name;
        this.model = model;
        this.status = status != null ? status : "IDLE";
        this.currentMaterial = currentMaterial;
        this.currentColour = currentColour;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (status == null) status = "IDLE";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentMaterial() {
        return currentMaterial;
    }

    public void setCurrentMaterial(String currentMaterial) {
        this.currentMaterial = currentMaterial;
    }

    public String getCurrentColour() {
        return currentColour;
    }

    public void setCurrentColour(String currentColour) {
        this.currentColour = currentColour;
    }

    public String getLoadedFilament() {
        return currentMaterial;
    }

    public String getColor() {
        return currentColour;
    }

    public String getColors() {
        return currentColour;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Printer printer = (Printer) o;
        return id != null && id.equals(printer.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
