package com.assessment.disputeportal.security.activity;

import java.lang.annotation.*;

/** Explicit server policy for user-interaction handlers; never a client keepalive flag. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthenticatedActivity {}
