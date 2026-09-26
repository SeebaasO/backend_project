package backend_project.backend_project.controller;

import backend_project.backend_project.model.Request.UpdatePasswordRequest;
import backend_project.backend_project.model.Request.UpdateUserRequest;
import backend_project.backend_project.model.Response.ApiResponse;
import backend_project.backend_project.model.Response.LoginResponse;
import backend_project.backend_project.model.Response.UserResponse;
import backend_project.backend_project.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> getUser(
            @RequestHeader(value = "Authorization") String authHeader) {

        return ResponseEntity.ok(ApiResponse.<UserResponse>builder()
                .result(true)
                .data(userService.getUserByToken(authHeader))
                .build());
    }

    @PatchMapping("/update-password")
    public ResponseEntity<ApiResponse<LoginResponse>> updatePassword(
            @RequestHeader(value = "Authorization") String authHeader,
            @Valid @RequestBody UpdatePasswordRequest updatePasswordRequest) {

        return ResponseEntity.ok(ApiResponse.<LoginResponse>builder()
                .result(true)
                .data(userService.updatePassword(authHeader, updatePasswordRequest))
                .build());
    }

    @PutMapping("/update-user")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @RequestHeader(value = "Authorization") String authHeader,
            @Valid @RequestBody UpdateUserRequest updateUserRequest) {

        return ResponseEntity.ok(ApiResponse.<UserResponse>builder()
                .result(true)
                .data(userService.updateUser(authHeader, updateUserRequest))
                .build());
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getUserAll(
            @RequestHeader(value = "Authorization") String authHeader) {

        return ResponseEntity.ok(ApiResponse.<List<UserResponse>>builder()
                .result(true)
                .data(userService.getUserAll(authHeader))
                .build());
    }
}
