package com.assessment.disputeportal.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Uses X-Real-IP only when the direct peer is the configured reverse proxy. */
@Component
public final class LoginClientAddressResolver {
  private final String trustedProxyAddress;

  public LoginClientAddressResolver(
      @Value("${portal.auth.rate-limit.trusted-proxy:}") String trustedProxyAddress) {
    this.trustedProxyAddress = normalizeAddress(trustedProxyAddress);
  }

  public String resolve(HttpServletRequest request) {
    String peer = normalizeAddress(request.getRemoteAddr());
    if (peer == null || !peer.equals(trustedProxyAddress)) return peerOrUnknown(peer);
    String forwarded = normalizeAddress(request.getHeader("X-Real-IP"));
    return forwarded == null ? peer : forwarded;
  }

  private static String peerOrUnknown(String address) {
    return address == null ? "unknown" : address;
  }

  private static String normalizeAddress(String address) {
    if (address == null || !address.matches("[0-9a-fA-F:.]+")) return null;
    try {
      return InetAddress.getByName(address).getHostAddress();
    } catch (UnknownHostException invalidAddress) {
      return null;
    }
  }
}
