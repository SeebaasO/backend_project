package backend_project.backend_project.entity;

import jakarta.persistence.*;
import lombok.*;

@Data
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "cars", uniqueConstraints = {@UniqueConstraint(columnNames = {"plate"})})
public class Car {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String plate;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String model;

    @Column(name = "car_year", nullable = false)
    private Integer year;

    @Column(nullable = false)
    private String color;

    @Column(name = "price_per_day", nullable = false)
    private Double pricePerDay;

    @Column(nullable = false)
    private Boolean available;
}
