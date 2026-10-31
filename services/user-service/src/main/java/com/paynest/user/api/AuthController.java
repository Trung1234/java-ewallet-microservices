package com.paynest.user.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paynest.user.domain.User;
import com.paynest.user.service.TokenService;
import com.paynest.user.service.UserService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService users;
    private final TokenService tokens;

    public AuthController(UserService users, TokenService tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(users.register(request.email(), request.password(), request.fullName()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        User user = users.authenticate(request.email(), request.password());
        return new TokenResponse(tokens.issue(user), "Bearer", tokens.ttlSeconds());
    }

    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 100) String password,
                                  @NotBlank @Size(max = 100) String fullName) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }
}
