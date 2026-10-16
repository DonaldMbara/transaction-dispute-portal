package com.assessment.disputeportal.auth.model.entity;

import com.assessment.disputeportal.auth.model.AccountRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * JPA entity — pure database mapping for the app_users table. Contains no business logic; all
 * invariants live in {@link com.assessment.disputeportal.auth.model.Account}.
 */
@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
@ToString(of = "id")
public class AppUser {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  @JsonIgnore
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AccountRole role;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "auth_version", nullable = false)
  private long authVersion;
}
