package com.riverfount.booksmanager.user.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Reader JPA entity, mapped to the table created by migration
 * V2__create_reader.sql. Separate from the domain; the conversion is done
 * by ReaderPersistenceAdapter.
 */
@Entity
@Table(name = "reader")
class ReaderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 30)
    private String document;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "guardian_name", length = 150)
    private String guardianName;

    @Column(name = "guardian_email", length = 254)
    private String guardianEmail;

    @Column(name = "guardian_phone", length = 30)
    private String guardianPhone;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean blocked;

    protected ReaderJpaEntity() {
        // required by JPA
    }

    ReaderJpaEntity(Long id, String name, String document, String email, String phone, LocalDate birthDate,
            String guardianName, String guardianEmail, String guardianPhone, boolean active, boolean blocked) {
        this.id = id;
        this.name = name;
        this.document = document;
        this.email = email;
        this.phone = phone;
        this.birthDate = birthDate;
        this.guardianName = guardianName;
        this.guardianEmail = guardianEmail;
        this.guardianPhone = guardianPhone;
        this.active = active;
        this.blocked = blocked;
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getDocument() {
        return document;
    }

    String getEmail() {
        return email;
    }

    String getPhone() {
        return phone;
    }

    LocalDate getBirthDate() {
        return birthDate;
    }

    String getGuardianName() {
        return guardianName;
    }

    String getGuardianEmail() {
        return guardianEmail;
    }

    String getGuardianPhone() {
        return guardianPhone;
    }

    boolean isActive() {
        return active;
    }

    boolean isBlocked() {
        return blocked;
    }
}
