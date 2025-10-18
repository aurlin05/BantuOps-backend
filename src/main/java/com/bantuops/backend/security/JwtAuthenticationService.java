package com.bantuops.backend.security;

import com.bantuops.backend.dto.auth.AuthenticationRequest;
import com.bantuops.backend.dto.auth.AuthenticationResponse;
import com.bantuops.backend.dto.auth.TokenResponse;
import com.bantuops.backend.entity.User;
import com.bantuops.backend.entity.UserRole;
import com.bantuops.backend.exception.InvalidTokenException;
import com.bantuops.backend.repository.UserRepository;
import com.bantuops.backend.service.AuditService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final AuditService auditService;

    @Value("${bantuops.jwt.secret}")
    private String jwtSecret;

    @Value("${bantuops.jwt.access-token-expiration:3600}")
    private long accessTokenExpiration; // 1 hour in seconds

    @Value("${bantuops.jwt.refresh-token-expiration:604800}")
    private long refreshTokenExpiration; // 7 days in seconds

    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    @Transactional
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        try {
            // Authenticate user credentials
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // Check if user is active
            if (!user.getIsActive()) {
                throw new BadCredentialsException("Account is deactivated");
            }

            // Generate tokens
            String accessToken = generateAccessToken(user);
            String refreshToken = generateRefreshToken(user);

            // Store refresh token in Redis
            storeRefreshToken(user.getId(), refreshToken);

            // Update last login
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);

            // Log authentication event
            auditService.logAuthenticationEvent(user.getId(), "LOGIN_SUCCESS", request.getIpAddress());

            log.info("User {} authenticated successfully", user.getEmail());

            return AuthenticationResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(accessTokenExpiration)
                    .user(mapUserToResponse(user))
                    .build();

        } catch (BadCredentialsException e) {
            auditService.logAuthenticationEvent(null, "LOGIN_FAILED", request.getIpAddress());
            log.warn("Authentication failed for email: {}", request.getEmail());
            throw new BadCredentialsException("Invalid credentials");
        }
    }

    public TokenResponse refreshToken(String refreshToken) {
        try {
            // Validate refresh token
            Claims claims = validateTokenClaims(refreshToken);
            String userEmail = claims.getSubject();

            // Check if refresh token exists in Redis
            String storedToken = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + userEmail);
            if (storedToken == null || !storedToken.equals(refreshToken)) {
                throw new InvalidTokenException("Invalid refresh token");
            }

            // Get user
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            if (!user.getIsActive()) {
                throw new BadCredentialsException("Account is deactivated");
            }

            // Generate new access token
            String newAccessToken = generateAccessToken(user);

            log.info("Token refreshed for user: {}", user.getEmail());

            return TokenResponse.builder()
                    .accessToken(newAccessToken)
                    .tokenType("Bearer")
                    .expiresIn(accessTokenExpiration)
                    .build();

        } catch (Exception e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            throw new InvalidTokenException("Invalid refresh token");
        }
    }

    public void revokeToken(String token) {
        try {
            Claims claims = validateTokenClaims(token);
            String userEmail = claims.getSubject();

            // Add token to blacklist
            long expiration = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (expiration > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + token,
                        "revoked",
                        expiration,
                        TimeUnit.MILLISECONDS
                );
            }

            // Remove refresh token
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + userEmail);

            auditService.logAuthenticationEvent(null, "TOKEN_REVOKED", null);
            log.info("Token revoked for user: {}", userEmail);

        } catch (Exception e) {
            log.warn("Token revocation failed: {}", e.getMessage());
        }
    }

    public boolean validateToken(String token) {
        try {
            // Check if token is blacklisted
            if (isTokenBlacklisted(token)) {
                return false;
            }

            validateTokenClaims(token);
            return true;
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public UserDetails extractUserDetails(String token) {
        Claims claims = validateTokenClaims(token);
        String email = claims.getSubject();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new CustomUserPrincipal(user);
    }

    private String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(accessTokenExpiration);

        Set<String> roles = user.getRoles().stream()
                .map(UserRole::getName)
                .collect(Collectors.toSet());

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("roles", roles)
                .claim("fullName", user.getFirstName() + " " + user.getLastName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(getSigningKey())
                .compact();
    }

    private String generateRefreshToken(User user) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(refreshTokenExpiration);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(getSigningKey())
                .compact();
    }

    private Claims validateTokenClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT token is expired: {}", e.getMessage());
            throw new InvalidTokenException("Token expired");
        } catch (MalformedJwtException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token format");
        } catch (SignatureException e) {
            log.debug("Invalid JWT signature: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token signature");
        } catch (Exception e) {
            log.debug("JWT token validation error: {}", e.getMessage());
            throw new InvalidTokenException("Token validation failed");
        }
    }

    private boolean isTokenBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }

    private void storeRefreshToken(Long userId, String refreshToken) {
        Claims claims = validateTokenClaims(refreshToken);
        String userEmail = claims.getSubject();
        
        long expiration = claims.getExpiration().getTime() - System.currentTimeMillis();
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + userEmail,
                refreshToken,
                expiration,
                TimeUnit.MILLISECONDS
        );
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    private AuthenticationResponse.UserInfo mapUserToResponse(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(UserRole::getName)
                .collect(Collectors.toSet());

        return AuthenticationResponse.UserInfo.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(roles)
                .isActive(user.getIsActive())
                .build();
    }
}