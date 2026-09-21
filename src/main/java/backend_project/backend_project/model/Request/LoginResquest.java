package backend_project.backend_project.model.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResquest {
    @NotBlank
    private  String username;
    @NotBlank
    private String password;
}
