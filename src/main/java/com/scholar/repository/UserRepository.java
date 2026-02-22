package com.scholar.repository;

import com.scholar.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    // ডাটাবেস থেকে ইমেইল দিয়ে ইউজার খোঁজার জন্য
    User findByEmail(String email);
}