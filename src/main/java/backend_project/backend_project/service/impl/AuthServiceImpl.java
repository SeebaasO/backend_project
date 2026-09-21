package backend_project.backend_project.service.impl;

import backend_project.backend_project.exception.BadRequestException;
import backend_project.backend_project.exception.InternalServerErrorException;
import backend_project.backend_project.exception.NotFoundException;
import backend_project.backend_project.model.Request.LoginResquest;
import backend_project.backend_project.model.Request.UserRegisterRequest;
import backend_project.backend_project.model.Response.LoginResponse;
import backend_project.backend_project.entity.User;
import backend_project.backend_project.repository.AuthRepository;
import backend_project.backend_project.service.JwtService;
import backend_project.backend_project.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    public LoginResponse registerUser(UserRegisterRequest userRegisterRequest){

        try {
            authRepository.findUserByUsername(userRegisterRequest.getUsername())
                    .ifPresent(user -> { throw new BadRequestException("Username ya esta en uso");
                    });

            User user = User.builder()
                    .firstname(userRegisterRequest.getFirstname())
                    .lastname(userRegisterRequest.getLastname())
                    .username(userRegisterRequest.getUsername())
                    .password(passwordEncoder.encode(userRegisterRequest.getPassword()))
                    .role(userRegisterRequest.getRole())
                    .build();

            authRepository.save(user);

            return LoginResponse.builder()
                    .accessToken(jwtService.generateJwtToken(user))
                    .build();


        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @Override
    public LoginResponse login(LoginResquest  loginResquest){

        try {
            User user = authRepository.findUserByUsername(loginResquest.getUsername())
                    .orElseThrow(() -> new NotFoundException("Credenciales invalidas"));

            if(!passwordEncoder.matches(loginResquest.getPassword(),user.getPassword())){
                throw  new NotFoundException("Credenciales invalidas");
            }

            return LoginResponse.builder()
                    .accessToken(jwtService.generateJwtToken(user))
                    .build();

        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

}
