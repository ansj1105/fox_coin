package com.foxya.coin.agency;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.agency.dto.AgencyStatusResponseDto;
import com.foxya.coin.agency.entities.AgencyMembership;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AgencyService extends BaseService {
    
    private final AgencyRepository agencyRepository;
    
    public AgencyService(PgPool pool, AgencyRepository agencyRepository) {
        super(pool);
        this.agencyRepository = agencyRepository;
    }
    
    public Future<AgencyStatusResponseDto> getAgencyStatus(Long userId) {
        return agencyRepository.getAgencyMembership(pool, userId)
            .map(membership -> {
                if (membership == null) {
                    return AgencyStatusResponseDto.builder()
                        .isJoined(false)
                        .joinedAt(null)
                        .agencyId(null)
                        .build();
                }
                
                return AgencyStatusResponseDto.builder()
                    .isJoined(true)
                    .joinedAt(membership.getJoinedAt())
                    .agencyId(membership.getAgencyId())
                    .build();
            });
    }
}

