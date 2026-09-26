package backend_project.backend_project.model.Request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCarRequest {

    @NotBlank
    private String plate;

    @NotBlank
    private String brand;

    @NotBlank
    private String model;

    @NotNull
    @Min(1900)
    @Max(2100)
    private Integer year;

    @NotBlank
    private String color;

    @NotNull
    @Positive
    private Double pricePerDay;
}
