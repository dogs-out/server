package com.dogsout.server.moderation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByStatusOrderByCreatedAtDesc(String status);

    List<Report> findTop200ByOrderByCreatedAtDesc();

    List<Report> findByReportedIdOrderByCreatedAtDesc(Long reportedId);

    long countByStatus(String status);

    long countByReportedId(Long reportedId);
}
