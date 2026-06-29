package com.dogsout.server.dog;

import com.dogsout.server.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dogs")
@Getter
@Setter
@NoArgsConstructor
public class Dog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String breed;
    private LocalDate dateOfBirth;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(columnDefinition = "TEXT")
    private String profilePicture;

    private Integer energyLevel;
    private String socialBehavior;

    @Column(columnDefinition = "TEXT")
    private String loves;

    private String offLeash;
    private Integer kidsComfort;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}