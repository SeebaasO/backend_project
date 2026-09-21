package backend_project.backend_project.service;

import backend_project.backend_project.model.Request.UpdatePasswordRequest;
import backend_project.backend_project.model.Request.UpdateUserRequest;
import backend_project.backend_project.model.Response.LoginResponse;
import backend_project.backend_project.model.Response.UserResponse;
import backend_project.backend_project.repository.UserRepository;

import java.util.List;

public interface UserService {

    UserResponse getUserByToken(String authHeader);

    LoginResponse updatePassword(String authHeader, UpdatePasswordRequest updatePasswordRequest);

    UserResponse updateUser(String authHeader, UpdateUserRequest updateUserRequest);

    List<UserResponse> getUserAll(String authHeader);
}
