package com.assessment.disputeportal.security.session;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("portal.session")
public record SessionTimeoutProperties(Duration idleTimeout) {}
