package com.aicoach.security;

/** Authenticated principal extracted from a valid access token. */
public record AuthUser(Long id, String email) {
}
