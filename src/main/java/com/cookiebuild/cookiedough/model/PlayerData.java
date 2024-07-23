package com.cookiebuild.cookiedough.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;

import java.util.Date;
import java.util.UUID;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class PlayerData {
    @Id
    private UUID id;
    private String name;

    private Date lastLogin;
    private Date createdAt;

    // Getters and setters
}
