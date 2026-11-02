package com.aicoach.auth;

import com.aicoach.auth.AuthDtos.*;
import com.aicoach.common.BadRequestException;
import com.aicoach.common.ConflictException;
import com.aicoach.security.JwtProperties;
import com.aicoach.security.JwtService;
import com.aicoach.user.User;
import com.aicoach.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProps;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService, JwtProperties jwtProps) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProps = jwtProps;
    }

    @Transactional
    public TokenResponse register(RegisterRequest req) {
        String email = req.email().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setDisplayName(req.displayName());
        if (req.timezone() != null) {
            user.setTimezone(validZone(req.timezone()));
        }
        return issueTokens(users.save(user));
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email().toLowerCase())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return issueTokens(user);
    }

    /** Rotates the refresh token: the old one is revoked and a new pair is issued. */
    @Transactional
    public TokenResponse refresh(RefreshRequest req) {
        RefreshToken token = refreshTokens.findByTokenHash(hash(req.refreshToken()))
                .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        token.setRevoked(true);
        return issueTokens(token.getUser());
    }

    @Transactional
    public void logout(RefreshRequest req) {
        refreshTokens.findByTokenHash(hash(req.refreshToken())).ifPresent(t -> t.setRevoked(true));
    }

    private TokenResponse issueTokens(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plus(jwtProps.refreshTokenTtl()));
        refreshTokens.save(token);

        String access = jwtService.createAccessToken(user.getId(), user.getEmail());
        return new TokenResponse(access, raw, jwtProps.accessTokenTtl().toSeconds());
    }

    private static String validZone(String zone) {
        try {
            return ZoneId.of(zone).getId();
        } catch (DateTimeException e) {
            throw new BadRequestException("Unknown timezone: " + zone);
        }
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
