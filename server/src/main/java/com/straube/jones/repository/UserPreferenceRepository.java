package com.straube.jones.repository;

import com.straube.jones.model.UserPreference;
import com.straube.jones.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
    Optional<UserPreference> findByUser(User user);
    Optional<UserPreference> findByUserId(Long userId);
    void deleteByUser(User user);
}
