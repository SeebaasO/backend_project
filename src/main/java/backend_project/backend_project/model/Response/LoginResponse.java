package backend_project.backend_project.model.Response;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
@Builder
public class LoginResponse {
    private String accessToken;
}
