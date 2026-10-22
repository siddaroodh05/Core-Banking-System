package com.siddu.auth.service;

import com.siddu.auth.dto.Response.SuccessResponse;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public SuccessResponse sendemail(String email, String otp) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(email);
        message.setSubject("Verify Your Email - Banking System");
        message.setText(
                "Hello,\n\n" +
                        "Your OTP for email verification is: " + otp + "\n\n" +
                        "This OTP expires in 5 minutes.\n\n" +
                        "If you did not request this verification, please ignore this email.\n\n" +
                        "Regards,\n" +
                        "Banking System"
        );

        mailSender.send(message);
        return new SuccessResponse("OTP sent successfully");
    }
}
