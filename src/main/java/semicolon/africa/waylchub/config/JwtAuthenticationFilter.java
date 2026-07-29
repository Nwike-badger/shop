package semicolon.africa.waylchub.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails;
import semicolon.africa.waylchub.mapper.UserMapper;
import semicolon.africa.waylchub.service.userService.JwtService;

import java.io.IOException;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        try {
            Claims claims = jwtService.parseToken(token).getBody();

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                CustomUserDetails principal = UserMapper.fromAccessTokenClaims(claims);

                var authToken = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception e) {
            log.debug("JWT auth rejected on {} {}: {}", request.getMethod(), request.getRequestURI(), e.toString());
            SecurityContextHolder.clearContext();   // invalid/expired/tampered → treated as anonymous
        }

        filterChain.doFilter(request, response);
    }
}