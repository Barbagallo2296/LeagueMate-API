package com.leaguemate.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String bio;

    @Column(name = "avatar_url")
    private String avatarUrl;

    private String phoneNumber;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}