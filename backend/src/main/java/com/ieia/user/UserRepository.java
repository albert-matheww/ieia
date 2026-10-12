package com.ieia.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("select u from User u left join fetch u.organization where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select u from User u left join fetch u.organization where u.id = :id")
    Optional<User> findByIdWithOrganization(@Param("id") UUID id);

    Page<User> findByOrganization_Id(UUID organizationId, Pageable pageable);
}
