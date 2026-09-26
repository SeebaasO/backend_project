package backend_project.backend_project.model.Response;

import lombok.*;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarResponse {

    private Integer id;
    private String plate;
    private String brand;
    private String model;
    private Integer year;
    private String color;
    private Double pricePerDay;
    private Boolean available;
}
