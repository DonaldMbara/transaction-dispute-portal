package com.assessment.disputeportal.security.activity;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activates Spring's scheduled task infrastructure for session housekeeping.
 * Kept in a dedicated @Configuration so @EnableScheduling does not
 * contaminate component classes (which causes duplicate scheduler
 * registration and flaky test behaviour when multiple contexts share a JVM).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfiguration {}
