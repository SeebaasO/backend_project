package backend_project.backend_project.model.Request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCarRequest {

    @NotNull
    @Positive
    private Double pricePerDay;

    @NotNull
    private Boolean available;
}
