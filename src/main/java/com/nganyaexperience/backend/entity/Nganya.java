package com.nganyaexperience.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "nganyas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Nganya {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    // e.g. "33 Seater"
    private String size;

    // stored as relative path e.g /uploads/nganyas/nganya_123.jpg
    private String imageUrl;
}
