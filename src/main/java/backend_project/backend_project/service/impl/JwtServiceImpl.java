package backend_project.backend_project.service.impl;

import backend_project.backend_project.exception.JwtAuthenticationException;
import backend_project.backend_project.entity.User;
import backend_project.backend_project.model.JwtValidate;
import backend_project.backend_project.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtServiceImpl implements JwtService {

    @Value("${jwt.secret}")
    private String secretCode;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    private SecretKey jwtSecret;

    @PostConstruct
    public void init() {
        if (secretCode == null || secretCode.trim().isEmpty()) {
            throw new IllegalStateException("JWT_SECRET must be configured in application.properties");
        }

        this.jwtSecret = Keys.hmacShaKeyFor(secretCode.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String generateJwtToken(User user) {
        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("id", user.getId())
                .claim("rol", user.getRole())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(jwtSecret, SignatureAlgorithm.HS512)
                .compact();
    }

    @Override
    public JwtValidate validateAccessToken(String authHeader) {
        try {
            String token = extractToken(authHeader);

            validateJwtToken(token);

            return JwtValidate.builder()
                    .id(getIdFromToken(token))
                    .username(getUsernameFromToken(token))
                    .role(getRolFromToken(token))
                    .build();
        }
        catch (Exception ex) {
            throw new JwtAuthenticationException(ex.getMessage());
        }
    }

    private String extractToken(String authHeader) throws BadRequestException {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new JwtAuthenticationException("Access Token Invalido");
    }

    private void validateJwtToken(String token) {
        try {
            Jwts.parser()
                    .setSigningKey(jwtSecret)
                    .build()
                    .parseClaimsJws(token);
        }
        catch (Exception ex) {
            throw new JwtAuthenticationException("Access Token Invalido");
        }
    }

    private String getUsernameFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(jwtSecret)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getSubject();
        } catch (Exception e) {
            throw new JwtAuthenticationException("Access Token Invalido");
        }
    }

    private String getRolFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(jwtSecret)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.get("rol", String.class);
        } catch (Exception e) {
            throw new JwtAuthenticationException("Access Token Invalido");
        }
    }

    private Integer getIdFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(jwtSecret)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.get("id", Integer.class);
        } catch (Exception e) {
            throw new JwtAuthenticationException("Access Token Invalido");
        }
    }

}
