package backend_project.backend_project.service;

import backend_project.backend_project.model.Request.LoginResquest;
import backend_project.backend_project.model.Request.UserRegisterRequest;
import backend_project.backend_project.model.Response.LoginResponse;


public interface AuthService {

    LoginResponse registerUser(UserRegisterRequest userRegisterRequest);

    LoginResponse login(LoginResquest loginResquest);
}
