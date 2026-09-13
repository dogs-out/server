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

    /** Prefix under which this photo's renditions live — see {@code UserPhoto.storageKey}. */
    @Column(name = "storage_key")
    private String storageKey;

    private Integer sortOrder;

    /**
     * Which part of the image is shown, as fractions of the whole — null means all
     * of it. Stored beside the photo rather than baked into the pixels so that
     * cropping in stays reversible: the file is always the full picture.
     */
    private Double cropX;
    private Double cropY;
    private Double cropWidth;
    private Double cropHeight;


    public DogPhoto(Dog dog, String storageKey, Integer sortOrder) {
        this.dog = dog;
        this.storageKey = storageKey;
        this.sortOrder = sortOrder;
    }
}