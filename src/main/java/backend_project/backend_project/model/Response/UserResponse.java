package backend_project.backend_project.model.Response;

import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String firstname;
    private String lastname;
    private String username;
    private String role;
}
