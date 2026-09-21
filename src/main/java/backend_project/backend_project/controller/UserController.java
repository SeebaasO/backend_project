package backend_project.backend_project.controller;

import backend_project.backend_project.model.Request.UpdatePasswordRequest;
import backend_project.backend_project.model.Request.UpdateUserRequest;
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
    public ResponseEntity<UserResponse> getUser(
            @RequestHeader(value = "Authorization") String authHeader) {

        return ResponseEntity.ok(userService.getUserByToken(authHeader));
    }

    @PatchMapping("/update-password")
    public ResponseEntity<LoginResponse> updatePassword(
            @RequestHeader(value = "Authorization") String authHeader,
            @Valid @RequestBody UpdatePasswordRequest updatePasswordRequest) {

        return ResponseEntity.ok(userService.updatePassword(authHeader, updatePasswordRequest));
    }

    @PutMapping("/update-user")
    public ResponseEntity<UserResponse> updateUser(
            @RequestHeader(value = "Authorization") String authHeader,
            @Valid @RequestBody UpdateUserRequest updateUserRequest) {

        return ResponseEntity.ok(userService.updateUser(authHeader, updateUserRequest));
    }

    @GetMapping("/all")
    public ResponseEntity<List<UserResponse>> getUserAll(
            @RequestHeader(value = "Authorization") String authHeader) {

        return ResponseEntity.ok(userService.getUserAll(authHeader));
    }
}
