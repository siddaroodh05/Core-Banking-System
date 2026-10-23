package com.siddu.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.siddu.auth.dto.Response.ApierrorResponse;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApierrorResponse> HandleEmailAlreadyExistsException(EmailAlreadyExistsException e){
        return ResponseEntity.status(HttpStatus.CONFLICT).
                body(new ApierrorResponse(HttpStatus.CONFLICT.name(),e.getMessage()));

    }
    @ExceptionHandler(InvalidCreditionalException.class)
    public ResponseEntity<ApierrorResponse> HandleInvalidCreditionalException(InvalidCreditionalException e){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                body(new ApierrorResponse(HttpStatus.BAD_REQUEST.name(),e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(error.getField(), error.getDefaultMessage())
                );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errors);
    }

    @ExceptionHandler(InvalidUserStateException.class)
    public ResponseEntity<ApierrorResponse> handleInvalidUserStateException(InvalidUserStateException e){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                body(new ApierrorResponse(HttpStatus.BAD_REQUEST.name(),e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApierrorResponse> handleIllegalStateException(IllegalStateException e){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                body(new ApierrorResponse(HttpStatus.BAD_REQUEST.name(),e.getMessage()));
    }

    @ExceptionHandler(ResourceExistException.class)
    public ResponseEntity<ApierrorResponse> handleResourceExistException(ResourceExistException e){
        return ResponseEntity.status(HttpStatus.CONFLICT).
                body(new ApierrorResponse(HttpStatus.CONFLICT.name(),e.getMessage()));
    }
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApierrorResponse> handleInvalidTokenException(InvalidTokenException e){
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApierrorResponse(HttpStatus.UNAUTHORIZED.name(),e.getMessage()));
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApierrorResponse> handleResourceNotFoundException(ResourceNotFoundException e){
        return ResponseEntity.status(HttpStatus.NOT_FOUND).
                body(new ApierrorResponse(HttpStatus.NOT_FOUND.name(),e.getMessage()));
    }


    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApierrorResponse> handleMissingBody(
            HttpMessageNotReadableException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApierrorResponse(
                        HttpStatus.BAD_REQUEST.name(),
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(ResourceNotMatchedException.class)
    public ResponseEntity<ApierrorResponse> handleResourceNotMatched(
            ResourceNotMatchedException ex
    ){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new
                ApierrorResponse(HttpStatus.BAD_REQUEST.name(),ex.getMessage()));
    }
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApierrorResponse> handleEmailAlreadyExistsException(EmailNotVerifiedException e){
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApierrorResponse("EMAIL_NOT_VERIFIED",e.getMessage()));
    }
}
