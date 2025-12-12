package com.foxya.coin.banner;

import com.foxya.coin.banner.dto.BannerListResponseDto;
import com.foxya.coin.banner.entities.Banner;
import com.foxya.coin.common.BaseService;
import io.vertx.core.Future;
import io.vertx.sqlclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class BannerService extends BaseService {
    
    private final BannerRepository bannerRepository;
    
    public BannerService(PgPool pool, BannerRepository bannerRepository) {
        super(pool);
        this.bannerRepository = bannerRepository;
    }
    
    /**
     * 배너 목록 조회
     */
    public Future<BannerListResponseDto> getBanners(String position) {
        return bannerRepository.getBanners(pool, position)
            .map(banners -> {
                List<BannerListResponseDto.BannerInfo> bannerInfos = banners.stream()
                    .map(banner -> BannerListResponseDto.BannerInfo.builder()
                        .id(banner.getId())
                        .title(banner.getTitle())
                        .imageUrl(banner.getImageUrl())
                        .linkUrl(banner.getLinkUrl())
                        .position(banner.getPosition())
                        .isActive(banner.getIsActive())
                        .startDate(banner.getStartDate())
                        .endDate(banner.getEndDate())
                        .build())
                    .collect(Collectors.toList());
                
                return BannerListResponseDto.builder()
                    .banners(bannerInfos)
                    .build();
            });
    }
    
    /**
     * 배너 클릭 이벤트 기록
     */
    public Future<Void> recordBannerClick(Long bannerId, Long userId, String ipAddress, String userAgent) {
        // 배너 존재 여부 확인
        return bannerRepository.existsBanner(pool, bannerId)
            .compose(exists -> {
                if (!exists) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("배너를 찾을 수 없습니다."));
                }
                
                return pool.withTransaction(client -> 
                    bannerRepository.recordBannerClick(client, bannerId, userId, ipAddress, userAgent)
                );
            });
    }
}

