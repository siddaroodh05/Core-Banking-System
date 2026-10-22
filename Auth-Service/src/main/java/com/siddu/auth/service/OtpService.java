package com.siddu.auth.service;

import com.siddu.auth.dto.Requests.OtpRequestBody;
import com.siddu.auth.dto.Requests.OtpSentRequestBody;
import com.siddu.auth.dto.Response.SuccessResponse;

import com.siddu.auth.exception.InvalidCreditionalException;
import com.siddu.auth.exception.ResourceNotFoundException;
import com.siddu.auth.exception.ResourceNotMatchedException;
import com.siddu.auth.repository.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;

@Service
public class OtpService {

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    private final EmailService emailService;

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final String OTP_PREFIX = "otp:";

    public OtpService(UserRepository userRepository,
                      StringRedisTemplate redisTemplate,
                      EmailService emailService) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.emailService = emailService;
    }

    @Transactional
    public SuccessResponse verifyotp(OtpRequestBody request) {

        String email = request.email();

        if (!userRepository.existsByEmail(email)) {
            throw new ResourceNotFoundException(
                    "Email is not registered");
        }

        if (userRepository.existsByEmailAndIsEmailVerifiedTrue(email)) {
            throw new ResourceNotMatchedException(
                    "Email is already verified");
        }

        String storedOtp = redisTemplate.opsForValue()
                .get(OTP_PREFIX + email);

        if (storedOtp == null || !storedOtp.equals(request.otp())) {
            throw new InvalidCreditionalException(
                    "Invalid or expired OTP");
        }

        int updated = userRepository.markEmailAsVerified(email);

        if (updated == 0) {
            throw new ResourceNotFoundException(
                    "Email is not registered");
        }

        redisTemplate.delete(OTP_PREFIX + email);

        return new SuccessResponse("Email verified successfully");
    }

    public SuccessResponse sendotp(OtpSentRequestBody request) {

        String email = request.email();

        if (!userRepository.existsByEmail(email)) {
            throw new ResourceNotFoundException(
                    "Email is not registered");
        }

        if (userRepository.existsByEmailAndIsEmailVerifiedTrue(email)) {
            throw new ResourceNotMatchedException(
                    "Email is already verified");
        }


        String otp = String.format("%06d",
                secureRandom.nextInt(1_000_000));

        redisTemplate.opsForValue().set(
                OTP_PREFIX + email,
                otp,
                OTP_TTL
        );

        return emailService.sendemail(request.email(),otp);
    }
}