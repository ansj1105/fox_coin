package com.foxya.coin.notice;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.notice.dto.NoticeListResponseDto;
import com.foxya.coin.notice.entities.Notice;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class NoticeService extends BaseService {
    
    private final NoticeRepository noticeRepository;
    
    public NoticeService(PgPool pool, NoticeRepository noticeRepository) {
        super(pool);
        this.noticeRepository = noticeRepository;
    }
    
    public Future<NoticeListResponseDto> getNotices(Integer limit, Integer offset) {
        return Future.all(
            noticeRepository.getNotices(pool, limit, offset),
            noticeRepository.getNoticeCount(pool)
        ).map(results -> {
            @SuppressWarnings("unchecked")
            List<Notice> notices = (List<Notice>) results.list().get(0);
            Long total = (Long) results.list().get(1);
            
            List<NoticeListResponseDto.NoticeInfo> noticeInfos = new ArrayList<>();
            for (Notice notice : notices) {
                noticeInfos.add(NoticeListResponseDto.NoticeInfo.builder()
                    .id(notice.getId())
                    .title(notice.getTitle())
                    .content(notice.getContent())
                    .createdAt(notice.getCreatedAt())
                    .isImportant(notice.getIsImportant())
                    .build());
            }
            
            return NoticeListResponseDto.builder()
                .notices(noticeInfos)
                .total(total)
                .limit(limit)
                .offset(offset)
                .build();
        });
    }
    
    public Future<Notice> getNoticeById(Long id) {
        return noticeRepository.getNoticeById(pool, id)
            .map(notice -> {
                if (notice == null) {
                    throw new com.foxya.coin.common.exceptions.NotFoundException("공지사항을 찾을 수 없습니다.");
                }
                return notice;
            });
    }
}

