package backend_project.backend_project.service.impl;

import backend_project.backend_project.entity.User;
import backend_project.backend_project.exception.*;
import backend_project.backend_project.model.JwtValidate;
import backend_project.backend_project.model.Request.UpdatePasswordRequest;
import backend_project.backend_project.model.Request.UpdateUserRequest;
import backend_project.backend_project.model.Response.LoginResponse;
import backend_project.backend_project.model.Response.UserResponse;
import backend_project.backend_project.repository.UserRepository;
import backend_project.backend_project.service.JwtService;
import backend_project.backend_project.service.UserService;
import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse getUserByToken(String authHeader) {

        try {
            JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

            User user = userRepository.findUserById(jwtValidate.getId());

            return UserResponse.builder()
                    .firstname(user.getFirstname())
                    .lastname(user.getLastname())
                    .username(user.getUsername())
                    .role(user.getRole())
                    .build();
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }

    }

    @Override
    public LoginResponse updatePassword(String authHeader, UpdatePasswordRequest  updatePasswordRequest) {

        try {
            JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

            User user = userRepository.findUserById(jwtValidate.getId());

            if(!passwordEncoder.matches(updatePasswordRequest.getOldPassword(), user.getPassword())) {
                throw new BadRequestException("La contraseña no es correcta");
            }

            user.setPassword(passwordEncoder.encode(updatePasswordRequest.getNewPassword()));
            userRepository.save(user);

            return LoginResponse.builder()
                    .accessToken(jwtService.generateJwtToken(user))
                    .build();
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }

    }

    @Override
    public UserResponse updateUser(String authHeader, UpdateUserRequest updateUserRequest) {

        try {
            JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

            User user = userRepository.findUserById(jwtValidate.getId());

            user.setFirstname(updateUserRequest.getFirstname());
            user.setLastname(updateUserRequest.getLastname());

            userRepository.save(user);

            return UserResponse.builder()
                    .firstname(user.getFirstname())
                    .lastname(user.getLastname())
                    .username(user.getUsername())
                    .role(user.getRole())
                    .build();
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }

    }

    @Override
    public List<UserResponse> getUserAll(String authHeader) {

        try {
            JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

            if(!jwtValidate.getRole().equals("admin")){
                throw new ForbiddenException("No esta autorizado");
            }

            List<User> users = userRepository.findAll();

            if(users.isEmpty()){
                throw new NoContentException("No se encontraron usuarios");
            }

            return users.stream()
                    .map(user -> new UserResponse(
                            user.getFirstname(),
                            user.getLastname(),
                            user.getUsername(),
                            user.getRole()
                    )).toList();
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }
}
