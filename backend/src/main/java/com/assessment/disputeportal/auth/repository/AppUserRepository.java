package com.assessment.disputeportal.auth.repository;

import com.assessment.disputeportal.auth.model.AccountIdentity;
import com.assessment.disputeportal.auth.model.entity.AppUser;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
  Optional<AppUser> findByEmail(String canonicalEmail);

  @Query(
      "select new com.assessment.disputeportal.auth.model.AccountIdentity(u.id,u.email,u.role,u.enabled,u.authVersion) from AppUser u where u.id = :id")
  Optional<AccountIdentity> identityById(@Param("id") UUID id);

  @Query(
      "select new com.assessment.disputeportal.auth.model.AccountIdentity(u.id,u.email,u.role,u.enabled,u.authVersion) from AppUser u where u.email = :email")
  Optional<AccountIdentity> identityByEmail(@Param("email") String canonicalEmail);

  /** Caller must hold the application service transaction for the entire mutation. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from AppUser u where u.id = :id")
  Optional<AppUser> findByIdForUpdate(@Param("id") UUID id);
}
