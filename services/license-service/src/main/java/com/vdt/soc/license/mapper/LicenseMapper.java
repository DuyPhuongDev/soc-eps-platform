package com.vdt.soc.license.mapper;

import com.vdt.soc.common.core.enumeration.LicensePlan;
import com.vdt.soc.common.core.enumeration.LicenseStatus;
import com.vdt.soc.license.dto.CreateLicenseRequest;
import com.vdt.soc.license.dto.LicenseResponse;
import com.vdt.soc.license.dto.PageResponse;
import com.vdt.soc.license.dto.UpdateLicenseRequest;
import com.vdt.soc.license.entity.License;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface LicenseMapper {

    default License toEntity(CreateLicenseRequest request){
        LicensePlan plan = request.getPlan();

        return License.builder()
                .tenantId(request.getTenantId())
                .epsQuota(plan.getEpsQuota())
                .mode(plan.getMode())
                .burstMultiplier(plan.getBurstMultiplier())
                .plan(plan)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(LicenseStatus.ACTIVE)
                .build();
    }

    LicenseResponse toResponse(License license);

    List<LicenseResponse> toResponseList(List<License> licenses);

    default PageResponse<LicenseResponse> toPageResponse(Page<License> licensePage) {
        return PageResponse.fromPage(licensePage.map(this::toResponse));
    }

    void updateEntity(UpdateLicenseRequest request, @MappingTarget License entity);
}
