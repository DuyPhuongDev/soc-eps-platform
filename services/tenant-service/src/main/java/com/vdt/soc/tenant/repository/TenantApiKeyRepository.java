package com.vdt.soc.tenant.repository;

import com.vdt.soc.tenant.entity.TenantApiKey;
import com.vdt.soc.common.model.enumeration.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantApiKeyRepository extends JpaRepository<TenantApiKey, UUID> {

    Optional<TenantApiKey> findByTenantId(UUID tenantId);

    Optional<TenantApiKey> findByApiKeyHash(String apiKeyHash);

    List<TenantApiKey> findAllByStatus(ApiKeyStatus status);
}
