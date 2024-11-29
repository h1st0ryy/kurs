package org.example.kurs.configuration;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final UserDetailsService userDetailsService;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String createToken(String username, Set<GrantedAuthority> authorities) {
        Claims claims = Jwts.claims().setSubject(username);
        claims.put("auth", authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList())
        );

        Date now = new Date();
        Date expiationDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiationDate)
                .signWith(getSigningKey())
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public Authentication getAuthentication(String token) {
        String username = getUsername(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    public Set<String> getRolesFromRequest(HttpServletRequest request) {
        // Извлекаем токен из запроса
        String token = resolveToken(request);

        // Если токен не найден, выбрасываем исключение или возвращаем пустой набор
        if (token == null) {
            throw new IllegalArgumentException("Токен не найден в запросе");
        }

        // Декодируем и извлекаем роли из токена
        return getRolesFromToken(token);
    }

    public Set<String> getRolesFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey()) // Используем секретный ключ для проверки подписи
                .build()
                .parseClaimsJws(token) // Разбираем токен
                .getBody(); // Извлекаем тело (claims)

        // Извлекаем роли из claim "auth"
        List<String> roles = (List<String>) claims.get("auth");

        // Возвращаем роли в виде набора (Set), чтобы избежать дублирования
        return roles.stream().collect(Collectors.toSet());
    }

    private String resolveToken(HttpServletRequest request) {
        // Получаем значение заголовка Authorization из запроса
        String bearerToken = request.getHeader("Authorization");

        // Проверяем, что заголовок не пустой и начинается с "Bearer "
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            // Если условие выполнено, возвращаем токен без префикса "Bearer "
            return bearerToken.substring(7);  // Убираем первые 7 символов ("Bearer ")
        }

        // Если заголовок пуст или не начинается с "Bearer ", возвращаем null
        return null;
    }
}

