package com.siddu.auth.dto.Requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OtpRequestBody (
        @Email(message = "provide valid email")
        @NotBlank(message = "email is required")
        String email,
        @NotBlank(message = "please provide otp")
        @Size(min = 6,max = 6,message = "user must enter 6 digit opt")
        String otp
){ }
