package backend_project.backend_project.service;

import backend_project.backend_project.entity.User;
import backend_project.backend_project.model.JwtValidate;

public interface JwtService {

    String generateJwtToken(User user);

    JwtValidate validateAccessToken (String authHeader);

}
