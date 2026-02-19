package com.foxya.coin.level;

import com.foxya.coin.mining.MiningRepository;
import com.foxya.coin.notification.NotificationService;
import com.foxya.coin.notification.entities.Notification;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.user.entities.User;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LevelServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MiningRepository miningRepository;

    @Mock
    private NotificationService notificationService;

    @Test
    void levelFromExp_matchesConfiguredThresholds() {
        assertThat(LevelService.levelFromExp(new BigDecimal("0"))).isEqualTo(1);
        assertThat(LevelService.levelFromExp(new BigDecimal("4.999"))).isEqualTo(1);
        assertThat(LevelService.levelFromExp(new BigDecimal("5"))).isEqualTo(2);
        assertThat(LevelService.levelFromExp(new BigDecimal("15"))).isEqualTo(3);
        assertThat(LevelService.levelFromExp(new BigDecimal("35"))).isEqualTo(4);
        assertThat(LevelService.levelFromExp(new BigDecimal("70"))).isEqualTo(5);
        assertThat(LevelService.levelFromExp(new BigDecimal("130"))).isEqualTo(6);
        assertThat(LevelService.levelFromExp(new BigDecimal("220"))).isEqualTo(7);
        assertThat(LevelService.levelFromExp(new BigDecimal("350"))).isEqualTo(8);
        assertThat(LevelService.levelFromExp(new BigDecimal("520"))).isEqualTo(9);
    }

    @Test
    void runLevelSyncBatch_updatesCandidatesAndCreatesI18nNotifications() {
        LevelService levelService = new LevelService(null, userRepository, miningRepository, notificationService);

        UserRepository.LevelSyncCandidate candidate1 = UserRepository.LevelSyncCandidate.builder()
            .userId(10L)
            .currentLevel(1)
            .computedLevel(2)
            .build();
        UserRepository.LevelSyncCandidate candidate2 = UserRepository.LevelSyncCandidate.builder()
            .userId(11L)
            .currentLevel(2)
            .computedLevel(4)
            .build();

        when(userRepository.findUsersRequiringLevelSync(any(), anyLong(), eq(50)))
            .thenReturn(
                Future.succeededFuture(List.of(candidate1, candidate2)),
                Future.succeededFuture(List.of())
            );
        when(userRepository.updateLevel(any(), anyLong(), anyInt()))
            .thenAnswer(invocation -> Future.succeededFuture(
                User.builder()
                    .id(invocation.getArgument(1))
                    .level(invocation.getArgument(2))
                    .build()
            ));
        when(notificationService.createNotificationIfAbsentByRelatedId(
            anyLong(), eq(NotificationType.LEVEL_UP), any(), any(), anyLong(), any()))
            .thenReturn(Future.succeededFuture((Notification) null));

        Integer updatedCount = await(levelService.runLevelSyncBatch(50));
        assertThat(updatedCount).isEqualTo(2);

        verify(userRepository, times(2)).updateLevel(any(), anyLong(), anyInt());

        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> relatedIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(notificationService, times(2)).createNotificationIfAbsentByRelatedId(
            anyLong(),
            eq(NotificationType.LEVEL_UP),
            eq("Level Up"),
            any(),
            relatedIdCaptor.capture(),
            metadataCaptor.capture()
        );

        List<Long> relatedIds = relatedIdCaptor.getAllValues();
        assertThat(relatedIds).containsExactly(2L, 4L);

        List<String> metadatas = metadataCaptor.getAllValues();
        JsonObject metadata1 = new JsonObject(metadatas.get(0));
        JsonObject metadata2 = new JsonObject(metadatas.get(1));

        assertThat(metadata1.getString("titleKey")).isEqualTo("notifications.levelUp.title");
        assertThat(metadata1.getString("messageKey")).isEqualTo("notifications.levelUp.message");
        assertThat(metadata1.getInteger("previousLevel")).isEqualTo(1);
        assertThat(metadata1.getInteger("newLevel")).isEqualTo(2);

        assertThat(metadata2.getString("titleKey")).isEqualTo("notifications.levelUp.title");
        assertThat(metadata2.getString("messageKey")).isEqualTo("notifications.levelUp.message");
        assertThat(metadata2.getInteger("previousLevel")).isEqualTo(2);
        assertThat(metadata2.getInteger("newLevel")).isEqualTo(4);
    }

    @Test
    void runLevelSyncBatch_whenNoCandidates_returnsZero() {
        LevelService levelService = new LevelService(null, userRepository, miningRepository, notificationService);

        when(userRepository.findUsersRequiringLevelSync(any(), anyLong(), eq(100)))
            .thenReturn(Future.succeededFuture(List.of()));

        Integer updatedCount = await(levelService.runLevelSyncBatch(100));
        assertThat(updatedCount).isEqualTo(0);

        verify(userRepository, never()).updateLevel(any(), anyLong(), anyInt());
        verify(notificationService, never()).createNotificationIfAbsentByRelatedId(
            anyLong(), any(), any(), any(), anyLong(), any());
    }

    private <T> T await(Future<T> future) {
        try {
            return future.toCompletionStage().toCompletableFuture().get(Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
