package com.siddu.auth.dto.Requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OtpSentRequestBody (
        @Email(message = "please provide valid email")
        @NotBlank(message = "email must not be blank")
        String email
){
}
