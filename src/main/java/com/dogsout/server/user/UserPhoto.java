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

    /**
     * Prefix under which this photo's renditions live in {@code PhotoStorage},
     * e.g. {@code photos/user/9f3c…}. Replaced the old {@code imageData} column,
     * which held the whole image as a base64 data URI — 700 kB a row, and the
     * reason a 15-profile feed came to 18 MB.
     *
     * <p>Nullable rather than NOT NULL: with {@code ddl-auto=update} Hibernate adds
     * this column to a table that already has rows, and a NOT NULL add would fail
     * outright. It is null only for a row the migration has not reached yet.
     */
    @Column(name = "storage_key")
    private String storageKey;

    private Integer sortOrder;

    public UserPhoto(User user, String storageKey, int sortOrder) {
        this.user = user;
        this.storageKey = storageKey;
        this.sortOrder = sortOrder;
    }
}
