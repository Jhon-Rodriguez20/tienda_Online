package com.fesc.tiendaOnline.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fesc.tiendaOnline.model.entity.UsuarioEntity;
import com.fesc.tiendaOnline.model.entity.UsuarioEstado;
import com.fesc.tiendaOnline.security.UserDetailsImpl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;

@Service
public class JwtService {

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.issuer:tienda-online-api}")
    private String issuer;

    @Value("${jwt.audience:tienda-online-client}")
    private String audience;

    @Value("${jwt.private-key-location}")
    private Resource privateKeyResource;

    @Value("${jwt.public-key-location}")
    private Resource publicKeyResource;

    private final Clock clock = Clock.systemUTC();

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    @PostConstruct
    void loadKeys() throws Exception {
        privateKey = readPrivateKey(privateKeyResource);
        publicKey = readPublicKey(publicKeyResource);
    }

    public String generateToken(UsuarioEntity usuario) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusMillis(expiration);

        return Jwts.builder()
                .header().add("typ", "JWT").and()
                .id(UUID.randomUUID().toString())
                .subject(usuario.getIdUsuario().toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(privateKey)
                .compact();
    }

    public UUID extractIdUsuario(String token) {
        return UUID.fromString(extractClaim(token, Claims::getSubject));
    }

    public Date extractExpirationToken(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .clockSkewSeconds(30)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token, UsuarioEntity usuario) {
        UUID idUsuario = extractIdUsuario(token);
        return idUsuario.equals(usuario.getIdUsuario())
                && UsuarioEstado.ACTIVO.equals(usuario.getEstado());
    }

    public boolean validateToken(String token, UserDetailsImpl userDetails) {
        Claims claims = extractAllClaims(token);
        return claims.getSubject().equals(userDetails.getUsuario().getIdUsuario().toString())
                && userDetails.isEnabled();
    }

    public Long getExpirationTimeToken() {
        return expiration / 1000;
    }

    private RSAPrivateKey readPrivateKey(Resource resource) throws Exception {
        String pem = readPem(resource);
        String key = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(key);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private RSAPublicKey readPublicKey(Resource resource) throws Exception {
        String pem = readPem(resource);
        String key = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(key);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }

    private String readPem(Resource resource) throws IOException {
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
