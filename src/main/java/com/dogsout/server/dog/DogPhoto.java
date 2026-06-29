package com.dogsout.server.dog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "dog_photos")
@Getter
@Setter
@NoArgsConstructor
public class DogPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "dog_id")
    private Dog dog;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String imageData;

    private Integer sortOrder;

    public DogPhoto(Dog dog, String imageData, Integer sortOrder) {
        this.dog = dog;
        this.imageData = imageData;
        this.sortOrder = sortOrder;
    }
}