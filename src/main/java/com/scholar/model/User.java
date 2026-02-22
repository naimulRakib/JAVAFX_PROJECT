package com.scholar.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users") // আপনার Supabase-এর 'users' টেবিলের সাথে কানেক্টেড
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    // 🌟 নতুন কলামগুলো আপনার SQL অনুযায়ী যুক্ত করা হলো
    @Column(name = "full_name")
    private String fullName;

    @Column(name = "username")
    private String username;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt; // Supabase নিজে থেকে এই সময় বসিয়ে নেবে

    // ডিফল্ট কনস্ট্রাক্টর
    public User() {}

    // রেজিস্ট্রেশনের জন্য কনস্ট্রাক্টর
    public User(String email, String password, String fullName, String username) {
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.username = username;
    }

    // --- Getters and Setters (Lombok ছাড়া) ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}