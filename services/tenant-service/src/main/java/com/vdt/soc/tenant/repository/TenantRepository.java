package com.vdt.soc.tenant.repository;

import com.vdt.soc.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByEmail(String email);

    boolean existsByEmail(String email);
}
