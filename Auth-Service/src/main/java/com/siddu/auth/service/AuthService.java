package com.siddu.auth.service;

import com.siddu.auth.Enums.*;
import com.siddu.auth.dto.Requests.LoginRequest;
import com.siddu.auth.dto.Requests.RegisterRequest;
import com.siddu.auth.dto.Response.*;
import com.siddu.auth.entity.*;
import com.siddu.auth.exception.*;
import com.siddu.auth.repository.*;
import com.siddu.auth.security.JwtService;
import com.siddu.auth.util.TokenBlockList;
import com.siddu.auth.util.TokenHashUtil;
import com.siddu.commonsecurity.Jwt.JwtValidator;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Service
@Transactional
public class AuthService {
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtValidator jwtValidator;
    private final TokenBlockList  tokenBlockList;
     @Autowired
    public AuthService(UserRepository userRepository, UserRoleRepository userRoleRepository, RoleRepository roleRepository,
                       SessionRepository sessionRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       JwtValidator jwtValidator, TokenBlockList tokenBlockList
     ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.sessionRepository = sessionRepository;
         this.passwordEncoder = passwordEncoder;
         this.jwtService = jwtService;
         this.jwtValidator = jwtValidator;
         this.tokenBlockList = tokenBlockList;

    }

    @Transactional
    public SuccessResponse registerUser(RegisterRequest registerRequest) {
         if(userRepository.existsByEmail(registerRequest.getEmail())) {
             throw new EmailAlreadyExistsException("Email already exists");
         }

        UserEntity user = UserEntity.builder()
                .email(registerRequest.getEmail())
                .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                .isEmailVerified(false)
                .status(UserStatus.ACTIVE)
                .build();

        user=userRepository.save(user);

        RoleEntity role=roleRepository.findByName(RoleName.USER).orElseThrow(() ->
                new IllegalStateException("role user not found"));

        UserRoleEntity userRole = UserRoleEntity.builder()
                .user(user)
                .role(role)
                .build();
        userRoleRepository.save(userRole);

        List<String> roles = userRoleRepository.findByUser(user)
                .stream()
                .map(ur -> ur.getRole().getName().name())
                .toList();

        return new SuccessResponse("please verify your email through otp before completing registration");

    }

    public AuthResult loginUser(LoginRequest loginRequest,String deviceInfo,String ipAddress) {
        UserEntity user=userRepository.findByEmail(loginRequest.getEmail()).
                orElseThrow(() -> new InvalidCreditionalException("invalid credentials"));

        if(!passwordEncoder.matches(loginRequest.getPassword(),user.getPasswordHash())) {
            throw new InvalidCreditionalException("invalid credentials");
        }
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException(
                    "Please verify your email before logging in"
            );
        }

        sessionRepository.deactivateAllByUser(user.getId());

        List<String> roles = userRoleRepository.findByUser(user)
                .stream()
                .map(ur -> ur.getRole().getName().name())
                .toList();

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(),roles);
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        SessionEntity session=SessionEntity.builder()
                .user(user)
                .refreshTokenHash(TokenHashUtil.hash(refreshToken))
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .isActive(true)
                .expiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60))
                .build();
        tokenBlockList.unblockUser(user.getId());

        sessionRepository.save(session);


        return new AuthResult(new UserResponse(user.getEmail(),roles,user.isEmailVerified()),accessToken,refreshToken);

    }

    public LogoutResponse logoutUser(UUID userId) {
         sessionRepository.deactivateAllByUser(userId);
         return new LogoutResponse("logout successfully");
    }

    @Transactional
    public TokenResponse rotateRefreshToken(String refreshToken) {

         if(!jwtValidator.isRefreshTokenValid(refreshToken)) {
             throw new InvalidTokenException("invalid token");
         }

         String Hashed_RefreshToken=TokenHashUtil.hash(refreshToken);

        SessionEntity session= sessionRepository.findByRefreshTokenHash(Hashed_RefreshToken)
                .orElseThrow(() -> new InvalidTokenException("invalid refresh token"));

        if(!session.isActive()){
            throw new InvalidTokenException("refresh token revoked");
        }

        if(session.getExpiresAt().isBefore(Instant.now())){
            session.setActive(false);
            throw new InvalidTokenException("Refresh token expired");
        }
        UUID userId = session.getUser().getId();
        String email=session.getUser().getEmail();
        List<String> roles=userRoleRepository.findRoleNamesByUserId(userId);

        String newAccessToken=jwtService.generateAccessToken(userId,email,roles);
        String newRefreshToken=jwtService.generateRefreshToken(userId);

        session.setRefreshTokenHash(TokenHashUtil.hash(newRefreshToken));


        return new TokenResponse(newAccessToken,newRefreshToken);
    }

}
