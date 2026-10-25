package com.rolelens.api.job.repository;

import com.rolelens.api.job.entity.Cv;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CvRepository extends JpaRepository<Cv, UUID> {

    Optional<Cv> findByFileSha256(byte[] fileSha256);
}
