package com.dogsout.server.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPhotoRepository extends JpaRepository<UserPhoto, Long> {
    List<UserPhoto> findByUserOrderBySortOrderAsc(User user);
    long countByUser(User user);
}
