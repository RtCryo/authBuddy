package com.homebuddy.auth.account;

/** Successful register or sign-in response. */
public record AuthResponse(String token, String email) {}
