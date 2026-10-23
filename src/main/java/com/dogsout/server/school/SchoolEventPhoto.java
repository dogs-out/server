package com.dogsout.server.school;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "school_event_photos")
@Getter
@Setter
@NoArgsConstructor
public class SchoolEventPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id")
    private SchoolEvent event;

    @Column(nullable = false)
    private String storageKey;

    private int sortOrder;
}
