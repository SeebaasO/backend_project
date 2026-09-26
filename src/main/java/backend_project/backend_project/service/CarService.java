package backend_project.backend_project.service;

import backend_project.backend_project.model.Request.CreateCarRequest;
import backend_project.backend_project.model.Request.UpdateCarRequest;
import backend_project.backend_project.model.Response.CarResponse;
import backend_project.backend_project.model.Response.CreateCarResponse;

import java.util.List;

public interface CarService {

    CreateCarResponse createCar(String authHeader, CreateCarRequest createCarRequest);

    List<CarResponse> getAllCars();

    CarResponse getCarById(Integer id);

    CarResponse updateCar(String authHeader, Integer id, UpdateCarRequest updateCarRequest);

    CarResponse deleteCar(String authHeader, Integer id);
}
