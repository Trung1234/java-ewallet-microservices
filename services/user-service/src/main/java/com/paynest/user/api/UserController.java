package com.paynest.user.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paynest.user.service.UserService;

@RestController
@RequestMapping("/users/me")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(users.get(jwt.getSubject()));
    }

    @PostMapping("/kyc")
    public UserResponse submitKyc(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody KycRequest request) {
        return UserResponse.from(users.submitKyc(jwt.getSubject(), request.documentNumber()));
    }

    /** @param documentNumber ID card number (9 or 12 digits in this simulation) */
    public record KycRequest(@NotBlank String documentNumber) {
    }
}
