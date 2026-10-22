package com.siddu.auth.controller;

import com.siddu.auth.dto.Requests.LoginRequest;
import com.siddu.auth.dto.Requests.OtpRequestBody;
import com.siddu.auth.dto.Requests.OtpSentRequestBody;
import com.siddu.auth.dto.Requests.RegisterRequest;
import com.siddu.auth.dto.Response.*;
import com.siddu.auth.service.AuthService;
import com.siddu.auth.service.OtpService;
import com.siddu.auth.util.TokenBlockList;
import com.siddu.commonsecurity.Jwt.JwtValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.siddu.auth.util.CookieUtil;
import java.util.UUID;

@RestController
public class AuthController {
    private final AuthService authService;
    private final CookieUtil cookieutil;
    private final JwtValidator jwtValidator;
    private final TokenBlockList tokenBlockList;
    private final OtpService otpService;

    @Autowired
    public AuthController(AuthService authService, CookieUtil cookieutil,
                          JwtValidator jwtValidator,TokenBlockList tokenBlockList,
                          OtpService otpService) {
        this.authService = authService;
        this.cookieutil=cookieutil;
        this.jwtValidator = jwtValidator;
        this.tokenBlockList = tokenBlockList;
        this.otpService = otpService;
    }
    @PostMapping("/Auth/register")
    public ResponseEntity<SuccessResponse> RegisterUser(@Valid @RequestBody RegisterRequest registerRequest
    ) {
        return ResponseEntity.ok(authService.registerUser(registerRequest));
    }

    @PostMapping("/Auth/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest loginRequest,
                                              HttpServletResponse response, HttpServletRequest request) {
        String deviceInfo=request.getHeader("User-Agent");
        String ipAddress=request.getRemoteAddr();
        AuthResult authResult=authService.loginUser(loginRequest,deviceInfo,ipAddress);
        cookieutil.addAccessToken(response,authResult.getToken());
        cookieutil.addRefreshToken(response,authResult.getRefreshToken());
        return ResponseEntity.ok(authResult.getUserResponse());
    }

    @PostMapping("/Auth/logout")
    public ResponseEntity<LogoutResponse> logout(@CookieValue("ACCESS_TOKEN") String accessToken,HttpServletResponse response) {
        UUID  userid=jwtValidator.extractUserId(accessToken);
        tokenBlockList.blockUser(accessToken,userid);
        LogoutResponse logoutResponse = authService.logoutUser(userid);
        cookieutil.clearAccessToken(response);
        cookieutil.clearRefreshToken(response);
        return ResponseEntity.ok(logoutResponse);

    }

    @PostMapping("/Auth/refresh")
    public ResponseEntity<?> rotateRefreshToken(@CookieValue("REFRESH_TOKEN") String refreshToken,HttpServletResponse response) {
        TokenResponse tokenresponse=authService.rotateRefreshToken(refreshToken);
        cookieutil.addAccessToken(response,tokenresponse.getToken());
        cookieutil.addRefreshToken(response,tokenresponse.getRefreshToken());
        return ResponseEntity.ok("refresh token rotated successfully");

    }

    @PostMapping("/Auth/send-otp")
    public ResponseEntity<SuccessResponse> sendOtp(@RequestBody OtpSentRequestBody request){

        return ResponseEntity.ok(otpService.sendotp(request));
    }
    @PostMapping("/Auth/verify-otp")
    public ResponseEntity<SuccessResponse> verifyOtp(@RequestBody OtpRequestBody request){
        return ResponseEntity.ok(otpService.verifyotp(request));
    }
    @PutMapping("/Auth/reset-password")
    public void resetPassword(){

    }

    @PutMapping("/Auth/change-password")
    public void changePassword(){

    }
}
