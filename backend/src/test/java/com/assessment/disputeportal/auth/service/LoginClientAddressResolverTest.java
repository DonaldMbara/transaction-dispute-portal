package com.assessment.disputeportal.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class LoginClientAddressResolverTest {
  @Test
  void forwardedAddressIsUsedOnlyFromTheConfiguredProxy() {
    var resolver = new LoginClientAddressResolver("172.30.255.2");
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("192.0.2.15");
    request.addHeader("X-Real-IP", "198.51.100.7");
    assertThat(resolver.resolve(request)).isEqualTo("192.0.2.15");

    request.setRemoteAddr("172.30.255.2");
    assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
  }
}
