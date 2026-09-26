package backend_project.backend_project.controller;

import backend_project.backend_project.model.Request.CreateCarRequest;
import backend_project.backend_project.model.Request.UpdateCarRequest;
import backend_project.backend_project.model.Response.ApiResponse;
import backend_project.backend_project.model.Response.CarResponse;
import backend_project.backend_project.model.Response.CreateCarResponse;
import backend_project.backend_project.service.CarService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/car")
public class CarController {

    private final CarService carService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateCarResponse>> createCar(
            @RequestHeader(value = "Authorization") String authHeader,
            @Valid @RequestBody CreateCarRequest createCarRequest) {

        return ResponseEntity.ok(ApiResponse.<CreateCarResponse>builder()
                .result(true)
                .data(carService.createCar(authHeader, createCarRequest))
                .build());
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<CarResponse>>> getAllCars() {

        return ResponseEntity.ok(ApiResponse.<List<CarResponse>>builder()
                .result(true)
                .data(carService.getAllCars())
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CarResponse>> getCarById(@PathVariable Integer id) {

        return ResponseEntity.ok(ApiResponse.<CarResponse>builder()
                .result(true)
                .data(carService.getCarById(id))
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CarResponse>> updateCar(
            @RequestHeader(value = "Authorization") String authHeader,
            @PathVariable Integer id,
            @Valid @RequestBody UpdateCarRequest updateCarRequest) {

        return ResponseEntity.ok(ApiResponse.<CarResponse>builder()
                .result(true)
                .data(carService.updateCar(authHeader, id, updateCarRequest))
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<CarResponse>> deleteCar(
            @RequestHeader(value = "Authorization") String authHeader,
            @PathVariable Integer id) {

        return ResponseEntity.ok(ApiResponse.<CarResponse>builder()
                .result(true)
                .data(carService.deleteCar(authHeader, id))
                .build());
    }
}
