package com.cookiebuild.cookiedough.model;

import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class PlayerData {
    @Id
    private UUID id;
    private String name;

    // Getters and setters
}
