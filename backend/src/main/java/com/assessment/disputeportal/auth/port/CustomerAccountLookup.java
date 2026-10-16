package com.assessment.disputeportal.auth.port;

import com.assessment.disputeportal.auth.model.AccountIdentity;
import java.util.Optional;

public interface CustomerAccountLookup {
  Optional<AccountIdentity> findByEmail(String email);
}
