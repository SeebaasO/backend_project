package backend_project.backend_project.repository;

import backend_project.backend_project.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Integer> {

    Optional<Reservation> findReservationById(Integer id);

    List<Reservation> findAllByUserId(Integer userId);

    boolean existsByCarIdAndStartDateLessThanAndEndDateGreaterThan(
            Integer carId, LocalDate endDate, LocalDate startDate);

    boolean existsByCarIdAndIdNotAndStartDateLessThanAndEndDateGreaterThan(
            Integer carId, Integer id, LocalDate endDate, LocalDate startDate);
}
