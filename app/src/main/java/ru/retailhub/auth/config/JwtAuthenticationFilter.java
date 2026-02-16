package ru.retailhub.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.retailhub.auth.service.JwtService;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Фильтр JWT-аутентификации.
 *
 * Для каждого запроса:
 *   1. Извлекает токен из заголовка Authorization: Bearer {token}
 *   2. Валидирует подпись и expiration
 *   3. Проверяет что это access-токен (не refresh)
 *   4. Устанавливает Authentication в SecurityContext
 *
 * Principal = UUID пользователя (строка).
 * Authorities = ROLE_MANAGER или ROLE_CONSULTANT.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (jwtService.isValid(token) && jwtService.isAccessToken(token)) {
                UUID userId = jwtService.extractUserId(token);
                String role = jwtService.extractRole(token);

                var auth = new UsernamePasswordAuthenticationToken(
                        userId.toString(),                              // principal = userId
                        null,                                           // credentials
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))  // ROLE_MANAGER / ROLE_CONSULTANT
                );

                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
