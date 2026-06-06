package dev.zen.story2script.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    private final AuthCookieFactory cookieFactory;
    private final AuthTokenService tokenService;
    private final AuthService authService;

    AuthTokenFilter(AuthCookieFactory cookieFactory, AuthTokenService tokenService, AuthService authService) {
        this.cookieFactory = cookieFactory;
        this.tokenService = tokenService;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            cookieFactory.read(request.getCookies())
                    .flatMap(tokenService::verify)
                    .map(authService::requireUser)
                    .ifPresent(user -> SecurityContextHolder.getContext().setAuthentication(authentication(user)));
        }

        filterChain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken authentication(AuthUser user) {
        return new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))
        );
    }
}
