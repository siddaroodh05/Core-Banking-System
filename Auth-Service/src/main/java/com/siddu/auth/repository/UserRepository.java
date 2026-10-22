package com.siddu.auth.repository;

import com.siddu.auth.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    @Query("select u.id from UserEntity u where u.email = :email")
    Optional<UUID> findIdByEmail(@Param("email") String email);


    boolean existsByEmail(String email);
    @Modifying
    @Query("""
    UPDATE UserEntity u
    SET u.isEmailVerified = true
    WHERE u.email = :email
    """)
    int markEmailAsVerified(@Param("email") String email);

    boolean existsByEmailAndIsEmailVerifiedTrue(String email);
}