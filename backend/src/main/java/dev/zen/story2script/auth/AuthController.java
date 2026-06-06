package dev.zen.story2script.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;
    private final AuthTokenService tokenService;
    private final AuthCookieFactory cookieFactory;

    AuthController(AuthService authService, AuthTokenService tokenService, AuthCookieFactory cookieFactory) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.cookieFactory = cookieFactory;
    }

    @PostMapping("/register")
    ResponseEntity<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        AuthUser user = authService.register(request);
        return signedIn(user);
    }

    @PostMapping("/login")
    ResponseEntity<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        AuthUser user = authService.login(request);
        return signedIn(user);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    @GetMapping("/me")
    AuthDtos.AuthResponse me(@AuthenticationPrincipal AuthUser user) {
        return new AuthDtos.AuthResponse(user);
    }

    private ResponseEntity<AuthDtos.AuthResponse> signedIn(AuthUser user) {
        String token = tokenService.issueToken(user.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.create(token).toString())
                .body(new AuthDtos.AuthResponse(user));
    }
}
