package com.dogsout.server.dog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DogPhotoRepository extends JpaRepository<DogPhoto, Long> {
    List<DogPhoto> findByDogOrderBySortOrderAsc(Dog dog);
    long countByDog(Dog dog);
}