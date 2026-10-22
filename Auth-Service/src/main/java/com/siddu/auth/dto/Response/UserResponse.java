package com.siddu.auth.dto.Response;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
@Getter
@AllArgsConstructor
public class UserResponse {
    private String email;
    private List<String> roles;
    private boolean isEmailVerified;

}
