package com.foxya.coin.review;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.review.dto.ReviewStatusResponseDto;
import com.foxya.coin.review.entities.Review;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReviewService extends BaseService {
    
    private final ReviewRepository reviewRepository;
    
    public ReviewService(PgPool pool, ReviewRepository reviewRepository) {
        super(pool);
        this.reviewRepository = reviewRepository;
    }
    
    public Future<ReviewStatusResponseDto> getReviewStatus(Long userId) {
        return reviewRepository.getReview(pool, userId)
            .map(review -> {
                if (review == null) {
                    return ReviewStatusResponseDto.builder()
                        .hasWrittenReview(false)
                        .reviewedAt(null)
                        .build();
                }
                
                return ReviewStatusResponseDto.builder()
                    .hasWrittenReview(true)
                    .reviewedAt(review.getReviewedAt())
                    .build();
            });
    }
    
    public Future<Review> writeReview(Long userId, String platform, String reviewId) {
        return reviewRepository.createReview(pool, userId, platform, reviewId);
    }
}

