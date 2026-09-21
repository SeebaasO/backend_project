package backend_project.backend_project.repository;

import backend_project.backend_project.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthRepository extends JpaRepository<User, Integer> {

    @Override
    User save(User user);

    Optional<User> findUserByUsername(String username);


}
