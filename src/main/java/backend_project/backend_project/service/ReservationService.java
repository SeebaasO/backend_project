package backend_project.backend_project.service;

import backend_project.backend_project.model.Request.CreateReservationRequest;
import backend_project.backend_project.model.Request.UpdateReservationRequest;
import backend_project.backend_project.model.Response.CreateReservationResponse;
import backend_project.backend_project.model.Response.ReservationResponse;

import java.util.List;

public interface ReservationService {

    CreateReservationResponse createReservation(String authHeader, CreateReservationRequest createReservationRequest);

    List<ReservationResponse> getAllReservations(String authHeader);

    ReservationResponse getReservationById(String authHeader, Integer id);

    ReservationResponse updateReservation(String authHeader, Integer id, UpdateReservationRequest updateReservationRequest);

    ReservationResponse deleteReservation(String authHeader, Integer id);
}
