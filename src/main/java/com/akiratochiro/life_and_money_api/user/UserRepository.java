package com.akiratochiro.life_and_money_api.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select u.credentialsVersion from User u where u.id = :id")
    Optional<Integer> findCredentialsVersionById(@Param("id") Long id);

}