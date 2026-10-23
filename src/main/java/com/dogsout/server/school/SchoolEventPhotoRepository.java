package com.dogsout.server.school;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolEventPhotoRepository extends JpaRepository<SchoolEventPhoto, Long> {
    List<SchoolEventPhoto> findByEventOrderBySortOrderAsc(SchoolEvent event);
}
