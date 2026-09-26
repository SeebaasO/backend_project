package backend_project.backend_project.service.impl;

import backend_project.backend_project.entity.Car;
import backend_project.backend_project.entity.Reservation;
import backend_project.backend_project.entity.User;
import backend_project.backend_project.exception.*;
import backend_project.backend_project.model.JwtValidate;
import backend_project.backend_project.model.Request.CreateReservationRequest;
import backend_project.backend_project.model.Request.UpdateReservationRequest;
import backend_project.backend_project.model.Response.CreateReservationResponse;
import backend_project.backend_project.model.Response.ReservationResponse;
import backend_project.backend_project.repository.CarRepository;
import backend_project.backend_project.repository.ReservationRepository;
import backend_project.backend_project.repository.UserRepository;
import backend_project.backend_project.service.JwtService;
import backend_project.backend_project.service.ReservationService;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Override
    public CreateReservationResponse createReservation(String authHeader, CreateReservationRequest createReservationRequest) {

        try {
            JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

            LocalDate startDate = createReservationRequest.getStartDate();
            LocalDate endDate = createReservationRequest.getEndDate();

            if (!endDate.isAfter(startDate)) {
                throw new BadRequestException("La fecha de fin debe ser posterior a la fecha de inicio");
            }

            Car car = carRepository.findCarById(createReservationRequest.getCarId())
                    .orElseThrow(() -> new NotFoundException("Carro no encontrado"));

            if (!car.getAvailable()) {
                throw new BadRequestException("El carro no esta disponible");
            }

            if (reservationRepository.existsByCarIdAndStartDateLessThanAndEndDateGreaterThan(
                    car.getId(), endDate, startDate)) {
                throw new BadRequestException("El carro ya esta reservado en esas fechas");
            }

            User user = userRepository.findUserById(jwtValidate.getId());

            long days = ChronoUnit.DAYS.between(startDate, endDate);

            Reservation reservation = Reservation.builder()
                    .user(user)
                    .car(car)
                    .startDate(startDate)
                    .endDate(endDate)
                    .totalPrice(days * car.getPricePerDay())
                    .build();

            Reservation saved = reservationRepository.save(reservation);

            car.setAvailable(false);
            carRepository.save(car);

            return CreateReservationResponse.builder()
                    .id(saved.getId())
                    .build();
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @Override
    public List<ReservationResponse> getAllReservations(String authHeader) {

        try {
            JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

            List<Reservation> reservations = jwtValidate.getRole().equals("admin")
                    ? reservationRepository.findAll()
                    : reservationRepository.findAllByUserId(jwtValidate.getId());

            if (reservations.isEmpty()) {
                throw new NoContentException("No se encontraron reservas");
            }

            return reservations.stream()
                    .map(this::toReservationResponse)
                    .toList();
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @Override
    public ReservationResponse getReservationById(String authHeader, Integer id) {

        try {
            JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

            Reservation reservation = reservationRepository.findReservationById(id)
                    .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));

            if (!reservation.getUser().getId().equals(jwtValidate.getId())
                    && !jwtValidate.getRole().equals("admin")) {
                throw new ForbiddenException("No esta autorizado");
            }

            return toReservationResponse(reservation);
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @Override
    public ReservationResponse updateReservation(String authHeader, Integer id, UpdateReservationRequest updateReservationRequest) {

        try {
            JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

            Reservation reservation = reservationRepository.findReservationById(id)
                    .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));

            if (!reservation.getUser().getId().equals(jwtValidate.getId())
                    && !jwtValidate.getRole().equals("admin")) {
                throw new ForbiddenException("No esta autorizado");
            }

            if (!reservation.getStartDate().isAfter(LocalDate.now())) {
                throw new BadRequestException("La reserva ya inicio y no se puede modificar");
            }

            LocalDate startDate = updateReservationRequest.getStartDate();
            LocalDate endDate = updateReservationRequest.getEndDate();

            if (!endDate.isAfter(startDate)) {
                throw new BadRequestException("La fecha de fin debe ser posterior a la fecha de inicio");
            }

            Car car = reservation.getCar();

            if (reservationRepository.existsByCarIdAndIdNotAndStartDateLessThanAndEndDateGreaterThan(
                    car.getId(), reservation.getId(), endDate, startDate)) {
                throw new BadRequestException("El carro ya esta reservado en esas fechas");
            }

            long days = ChronoUnit.DAYS.between(startDate, endDate);

            reservation.setStartDate(startDate);
            reservation.setEndDate(endDate);
            reservation.setTotalPrice(days * car.getPricePerDay());

            reservationRepository.save(reservation);

            return toReservationResponse(reservation);
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @Override
    public ReservationResponse deleteReservation(String authHeader, Integer id) {

        try {
            JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

            Reservation reservation = reservationRepository.findReservationById(id)
                    .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));

            boolean isAdmin = jwtValidate.getRole().equals("admin");

            if (!reservation.getUser().getId().equals(jwtValidate.getId()) && !isAdmin) {
                throw new ForbiddenException("No esta autorizado");
            }

            if (!isAdmin && !reservation.getStartDate().isAfter(LocalDate.now())) {
                throw new BadRequestException("La reserva ya inicio y no se puede cancelar");
            }

            ReservationResponse response = toReservationResponse(reservation);

            reservationRepository.delete(reservation);

            return response;
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    private ReservationResponse toReservationResponse(Reservation reservation) {

        return ReservationResponse.builder()
                .id(reservation.getId())
                .userId(reservation.getUser().getId())
                .username(reservation.getUser().getUsername())
                .carId(reservation.getCar().getId())
                .plate(reservation.getCar().getPlate())
                .brand(reservation.getCar().getBrand())
                .model(reservation.getCar().getModel())
                .startDate(reservation.getStartDate())
                .endDate(reservation.getEndDate())
                .totalPrice(reservation.getTotalPrice())
                .build();
    }
}
