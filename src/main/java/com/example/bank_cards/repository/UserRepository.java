package com.example.bank_cards.repository;

import com.example.bank_cards.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<AppUser, UUID> {

    @NonNull
    Optional<AppUser> findByEmail(@NonNull String email);
}
