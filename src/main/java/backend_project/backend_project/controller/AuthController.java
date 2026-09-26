package backend_project.backend_project.controller;

import backend_project.backend_project.model.Request.LoginResquest;
import backend_project.backend_project.model.Request.UserRegisterRequest;
import backend_project.backend_project.model.Response.ApiResponse;
import backend_project.backend_project.model.Response.LoginResponse;
import backend_project.backend_project.service.AuthService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService userService;

    @PostMapping("register")
    public ResponseEntity<ApiResponse<LoginResponse>> register(@Validated @RequestBody UserRegisterRequest userRegisterRequest) {

        return ResponseEntity.ok(ApiResponse.<LoginResponse>builder()
                .result(true)
                .data(userService.registerUser(userRegisterRequest))
                .build());
    }

    @PostMapping("login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Validated @RequestBody LoginResquest loginResquest) {

        return ResponseEntity.ok(ApiResponse.<LoginResponse>builder()
                .result(true)
                .data(userService.login(loginResquest))
                .build());
    }

}
