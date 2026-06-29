package com.dogsout.server.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_photos")
@Getter
@Setter
@NoArgsConstructor
public class UserPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String imageData;

    private Integer sortOrder;

    public UserPhoto(User user, String imageData, int sortOrder) {
        this.user = user;
        this.imageData = imageData;
        this.sortOrder = sortOrder;
    }
}
