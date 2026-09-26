package backend_project.backend_project.repository;

import backend_project.backend_project.entity.Car;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CarRepository extends JpaRepository<Car, Integer> {

    Optional<Car> findCarById(Integer id);

    Optional<Car> findCarByPlate(String plate);

    List<Car> findAllByAvailableTrue();
}
