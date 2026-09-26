package backend_project.backend_project.service.impl;

import backend_project.backend_project.entity.Car;
import backend_project.backend_project.exception.*;
import backend_project.backend_project.model.JwtValidate;
import backend_project.backend_project.model.Request.CreateCarRequest;
import backend_project.backend_project.model.Request.UpdateCarRequest;
import backend_project.backend_project.model.Response.CarResponse;
import backend_project.backend_project.model.Response.CreateCarResponse;
import backend_project.backend_project.repository.CarRepository;
import backend_project.backend_project.service.CarService;
import backend_project.backend_project.service.JwtService;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CarServiceImpl implements CarService {

    private final CarRepository carRepository;
    private final JwtService jwtService;

    @Override
    public CreateCarResponse createCar(String authHeader, CreateCarRequest createCarRequest) {

        try {
            JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

            if (!jwtValidate.getRole().equals("admin")) {
                throw new ForbiddenException("No esta autorizado");
            }

            String plate = createCarRequest.getPlate().trim().toUpperCase();

            carRepository.findCarByPlate(plate)
                    .ifPresent(c -> { throw new BadRequestException("La placa ya esta registrada"); });

            Car car = Car.builder()
                    .plate(plate)
                    .brand(createCarRequest.getBrand())
                    .model(createCarRequest.getModel())
                    .year(createCarRequest.getYear())
                    .color(createCarRequest.getColor())
                    .pricePerDay(createCarRequest.getPricePerDay())
                    .available(true)
                    .build();

            Car saved = carRepository.save(car);

            return CreateCarResponse.builder()
                    .id(saved.getId())
                    .build();
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @Override
    public List<CarResponse> getAllCars() {

        try {
            List<Car> cars = carRepository.findAll();

            if (cars.isEmpty()) {
                throw new NoContentException("No se encontraron carros");
            }

            return cars.stream()
                    .map(car -> CarResponse.builder()
                            .id(car.getId())
                            .plate(car.getPlate())
                            .brand(car.getBrand())
                            .model(car.getModel())
                            .year(car.getYear())
                            .color(car.getColor())
                            .pricePerDay(car.getPricePerDay())
                            .available(car.getAvailable())
                            .build())
                    .toList();
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @Override
    public CarResponse getCarById(Integer id) {

        try {
            Car car = carRepository.findCarById(id)
                    .orElseThrow(() -> new NotFoundException("Carro no encontrado"));

            return CarResponse.builder()
                    .id(car.getId())
                    .plate(car.getPlate())
                    .brand(car.getBrand())
                    .model(car.getModel())
                    .year(car.getYear())
                    .color(car.getColor())
                    .pricePerDay(car.getPricePerDay())
                    .available(car.getAvailable())
                    .build();
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @Override
    public CarResponse updateCar(String authHeader, Integer id, UpdateCarRequest updateCarRequest) {

        try {
            JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

            if (!jwtValidate.getRole().equals("admin")) {
                throw new ForbiddenException("No esta autorizado");
            }

            Car car = carRepository.findCarById(id)
                    .orElseThrow(() -> new NotFoundException("Carro no encontrado"));

            car.setPricePerDay(updateCarRequest.getPricePerDay());
            car.setAvailable(updateCarRequest.getAvailable());

            carRepository.save(car);

            return CarResponse.builder()
                    .id(car.getId())
                    .plate(car.getPlate())
                    .brand(car.getBrand())
                    .model(car.getModel())
                    .year(car.getYear())
                    .color(car.getColor())
                    .pricePerDay(car.getPricePerDay())
                    .available(car.getAvailable())
                    .build();
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @Override
    public CarResponse deleteCar(String authHeader, Integer id) {

        try {
            JwtValidate jwtValidate = jwtService.validateAccessToken(authHeader);

            if (!jwtValidate.getRole().equals("admin")) {
                throw new ForbiddenException("No esta autorizado");
            }

            Car car = carRepository.findCarById(id)
                    .orElseThrow(() -> new NotFoundException("Carro no encontrado"));

            carRepository.delete(car);

            return CarResponse.builder()
                    .id(car.getId())
                    .plate(car.getPlate())
                    .brand(car.getBrand())
                    .model(car.getModel())
                    .year(car.getYear())
                    .color(car.getColor())
                    .pricePerDay(car.getPricePerDay())
                    .available(car.getAvailable())
                    .build();
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }
}
