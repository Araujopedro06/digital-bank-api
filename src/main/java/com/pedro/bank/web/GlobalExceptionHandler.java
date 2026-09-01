package com.pedro.bank.web;

import com.pedro.bank.domain.InsufficientFundsException;
import com.pedro.bank.security.InvalidStepUpTokenException;
import com.pedro.bank.service.AccountNotFoundException;
import com.pedro.bank.service.EmailAlreadyUsedException;
import com.pedro.bank.service.FaceNotEnrolledException;
import com.pedro.bank.service.FaceVerificationFailedException;
import com.pedro.bank.service.InvalidFaceDescriptorException;
import com.pedro.bank.service.InvalidProfilePhotoException;
import com.pedro.bank.service.SameAccountTransferException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ApiError(int status, String message, Map<String, String> fieldErrors) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> handleInsufficientFunds(InsufficientFundsException e) {
        // The exception carries the balance and account number for the logs; the
        // response must not echo them back over the wire.
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds", null);
    }

    @ExceptionHandler(SameAccountTransferException.class)
    public ResponseEntity<ApiError> handleSameAccount(SameAccountTransferException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage(), null);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiError> handleAccountNotFound(AccountNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, "Account not found", null);
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ApiError> handleEmailInUse(EmailAlreadyUsedException e) {
        return build(HttpStatus.CONFLICT, e.getMessage(), null);
    }

    @ExceptionHandler(FaceVerificationFailedException.class)
    public ResponseEntity<ApiError> handleFaceMismatch(FaceVerificationFailedException e) {
        // No distance or threshold in the body: that would let a caller hill-climb
        // towards a matching descriptor.
        return build(HttpStatus.UNAUTHORIZED, "Face did not match", null);
    }

    @ExceptionHandler(FaceNotEnrolledException.class)
    public ResponseEntity<ApiError> handleFaceNotEnrolled(FaceNotEnrolledException e) {
        return build(HttpStatus.PRECONDITION_REQUIRED, "No face enrolled", null);
    }

    @ExceptionHandler(InvalidFaceDescriptorException.class)
    public ResponseEntity<ApiError> handleInvalidDescriptor(InvalidFaceDescriptorException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), null);
    }

    @ExceptionHandler(InvalidStepUpTokenException.class)
    public ResponseEntity<ApiError> handleInvalidStepUpToken(InvalidStepUpTokenException e) {
        return build(HttpStatus.FORBIDDEN, "Face confirmation required", null);
    }

    @ExceptionHandler(InvalidProfilePhotoException.class)
    public ResponseEntity<ApiError> handleInvalidPhoto(InvalidProfilePhotoException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Image must be 2 MB or smaller", null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException e) {
        // Deliberately vague: do not leak whether the e-mail exists.
        return build(HttpStatus.UNAUTHORIZED, "Invalid e-mail or password", null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message,
                                           Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, fieldErrors));
    }
}
