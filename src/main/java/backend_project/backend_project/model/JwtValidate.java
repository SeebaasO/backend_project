package backend_project.backend_project.model;

import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtValidate {
    private Integer id;
    private String username;
    private String role;
}
