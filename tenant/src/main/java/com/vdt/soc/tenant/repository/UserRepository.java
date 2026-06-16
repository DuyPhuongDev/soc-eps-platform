package com.vdt.soc.tenant.repository;

import com.vdt.soc.tenant.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    List<User> findByTenantId(UUID tenantId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
