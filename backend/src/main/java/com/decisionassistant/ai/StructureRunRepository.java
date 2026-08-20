package com.decisionassistant.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StructureRunRepository extends JpaRepository<StructureRun, UUID> {
}
