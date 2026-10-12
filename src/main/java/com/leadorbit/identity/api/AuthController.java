package com.leadorbit.identity.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.leadorbit.common.api.ApiResponse;
import com.leadorbit.identity.dto.AuthTokensResponse;
import com.leadorbit.identity.dto.LogoutRequest;
import com.leadorbit.identity.dto.RefreshRequest;
import com.leadorbit.identity.dto.SignInRequest;
import com.leadorbit.identity.dto.UserSummary;
import com.leadorbit.identity.service.AuthService;
import com.leadorbit.identity.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/sign-in")
    public ApiResponse<AuthTokensResponse> signIn(@Valid @RequestBody SignInRequest request,
                                                  HttpServletRequest http) {
        AuthTokensResponse tokens = authService.signIn(request, http.getHeader("User-Agent"), http.getRemoteAddr());
        return ApiResponse.ok("Signed in", tokens);
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokensResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                                   HttpServletRequest http) {
        AuthTokensResponse tokens = authService.refresh(request, http.getHeader("User-Agent"), http.getRemoteAddr());
        return ApiResponse.ok("Tokens refreshed", tokens);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok("Signed out");
    }

    @GetMapping("/me")
    public ApiResponse<UserSummary> me() {
        return ApiResponse.ok(userService.currentUser());
    }
}