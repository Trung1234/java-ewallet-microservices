package com.paynest.user.service;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.paynest.user.domain.KycStatus;
import com.paynest.user.domain.User;
import com.paynest.user.domain.UserRepository;
import com.paynest.web.ApiException;

@Service
public class UserService {

    /** Vietnamese ID numbers: 9 digits (CMND) or 12 digits (CCCD). */
    private static final Pattern ID_NUMBER = Pattern.compile("\\d{9}|\\d{12}");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String email, String password, String fullName) {
        String normalized = normalize(email);
        if (users.existsByEmail(normalized)) {
            throw emailTaken();
        }
        try {
            return users.save(new User(normalized, passwordEncoder.encode(password), fullName.trim()));
        } catch (DuplicateKeyException registeredConcurrently) {
            throw emailTaken();
        }
    }

    public User authenticate(String email, String password) {
        return users.findByEmail(normalize(email))
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Wrong email or password"));
    }

    public User get(String userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
    }

    /** Simulated KYC provider: a well-formed ID number is verified, anything else is rejected. */
    public User submitKyc(String userId, String documentNumber) {
        User user = get(userId);
        user.updateKyc(ID_NUMBER.matcher(documentNumber).matches() ? KycStatus.VERIFIED : KycStatus.REJECTED);
        return users.save(user);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException emailTaken() {
        return ApiException.conflict("EMAIL_TAKEN", "This email is already registered");
    }
}
