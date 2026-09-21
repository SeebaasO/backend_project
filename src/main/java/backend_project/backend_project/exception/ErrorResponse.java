package backend_project.backend_project.exception;

import lombok.*;

@Data
@Builder
@Getter
@Setter
@AllArgsConstructor
public class ErrorResponse {
    private String message;
    private int statusCode;
}
