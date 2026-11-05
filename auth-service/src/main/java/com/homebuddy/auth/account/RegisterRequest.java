package com.homebuddy.auth.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Registration input; the email is trimmed before validation. */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @ByteLength(min = 8, max = 72) String password) {

    public RegisterRequest {
        email = email == null ? null : email.trim();
    }
}
