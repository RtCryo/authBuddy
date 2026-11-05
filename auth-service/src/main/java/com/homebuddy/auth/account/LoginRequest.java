package com.homebuddy.auth.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sign-in input; the email is trimmed before validation. */
public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email, @NotBlank String password) {

    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
