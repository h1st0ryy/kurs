package org.example.kurs.repository;

import org.example.kurs.model.LicenseType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LicenseTypeRepository extends JpaRepository<LicenseType, Long> {

    // Метод для поиска типа лицензии по ID
    Optional<LicenseType> findById(Long id);

    // Можно добавить другие методы, например, для поиска по имени или описанию
    Optional<LicenseType> findByName(String name);
}
