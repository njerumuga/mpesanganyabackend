package com.nganyaexperience.backend.repository;

import com.nganyaexperience.backend.entity.Event;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    // Fetch events WITH tickets in one query (fixes N+1 problem)
    @Override
    @EntityGraph(attributePaths = "tickets")
    List<Event> findAll();
}
