package backend_project.backend_project.controller;

import backend_project.backend_project.model.Request.CreateReservationRequest;
import backend_project.backend_project.model.Request.UpdateReservationRequest;
import backend_project.backend_project.model.Response.ApiResponse;
import backend_project.backend_project.model.Response.CreateReservationResponse;
import backend_project.backend_project.model.Response.ReservationResponse;
import backend_project.backend_project.service.ReservationService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/reservation")
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateReservationResponse>> createReservation(
            @RequestHeader(value = "Authorization") String authHeader,
            @Valid @RequestBody CreateReservationRequest createReservationRequest) {

        return ResponseEntity.ok(ApiResponse.<CreateReservationResponse>builder()
                .result(true)
                .data(reservationService.createReservation(authHeader, createReservationRequest))
                .build());
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getAllReservations(
            @RequestHeader(value = "Authorization") String authHeader) {

        return ResponseEntity.ok(ApiResponse.<List<ReservationResponse>>builder()
                .result(true)
                .data(reservationService.getAllReservations(authHeader))
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservationById(
            @RequestHeader(value = "Authorization") String authHeader,
            @PathVariable Integer id) {

        return ResponseEntity.ok(ApiResponse.<ReservationResponse>builder()
                .result(true)
                .data(reservationService.getReservationById(authHeader, id))
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ReservationResponse>> updateReservation(
            @RequestHeader(value = "Authorization") String authHeader,
            @PathVariable Integer id,
            @Valid @RequestBody UpdateReservationRequest updateReservationRequest) {

        return ResponseEntity.ok(ApiResponse.<ReservationResponse>builder()
                .result(true)
                .data(reservationService.updateReservation(authHeader, id, updateReservationRequest))
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<ReservationResponse>> deleteReservation(
            @RequestHeader(value = "Authorization") String authHeader,
            @PathVariable Integer id) {

        return ResponseEntity.ok(ApiResponse.<ReservationResponse>builder()
                .result(true)
                .data(reservationService.deleteReservation(authHeader, id))
                .build());
    }
}
