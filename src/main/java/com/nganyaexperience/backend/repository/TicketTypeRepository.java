package com.nganyaexperience.backend.repository;

import com.nganyaexperience.backend.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {
}
