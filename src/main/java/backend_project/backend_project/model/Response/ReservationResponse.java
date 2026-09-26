package backend_project.backend_project.model.Response;

import lombok.*;

import java.time.LocalDate;

@Data
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {

    private Integer id;
    private Integer userId;
    private String username;
    private Integer carId;
    private String plate;
    private String brand;
    private String model;
    private LocalDate startDate;
    private LocalDate endDate;
    private Double totalPrice;
}
