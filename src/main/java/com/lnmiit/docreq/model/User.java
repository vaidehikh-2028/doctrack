package com.lnmiit.docreq.model;

import com.lnmiit.docreq.domain.Enums.AccountStatus;
import com.lnmiit.docreq.domain.Enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A system user. The design's abstract User with four subclasses is mapped to a
 * single table with a role column: the subclasses differ only in behaviour, which
 * lives in the services and is enforced by role-based access control.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(length = 50)
    private String contactInfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected User() {}

    public User(String username, String name, String email, String contactInfo, UserRole role, String passwordHash) {
        this.username = username;
        this.name = name;
        this.email = email;
        this.contactInfo = contactInfo;
        this.role = role;
        this.passwordHash = passwordHash;
    }

    public boolean isActive() { return accountStatus == AccountStatus.ACTIVE; }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getContactInfo() { return contactInfo; }
    public UserRole getRole() { return role; }
    public AccountStatus getAccountStatus() { return accountStatus; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }
    public void setRole(UserRole role) { this.role = role; }
    public void setAccountStatus(AccountStatus accountStatus) { this.accountStatus = accountStatus; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
