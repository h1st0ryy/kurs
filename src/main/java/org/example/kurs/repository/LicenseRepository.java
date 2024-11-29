package org.example.kurs.repository;

import org.example.kurs.model.License;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LicenseRepository extends JpaRepository<License, Long> {
    // Метод для поиска лицензии по коду
    Optional<License> findByCode(String code);



}

