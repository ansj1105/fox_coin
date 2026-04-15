package com.foxya.coin.user;

import com.foxya.coin.auth.EmailVerificationRepository;
import com.foxya.coin.security.OfflinePayAttestationService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.utils.EmailService;
import com.foxya.coin.subscription.SubscriptionService;
import com.foxya.coin.subscription.dto.SubscriptionStatusResponseDto;
import com.foxya.coin.user.dto.ProfileImageUploadResponseDto;
import com.foxya.coin.user.entities.User;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.pgclient.PgPool;
import io.vertx.redis.client.RedisAPI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceProfileImageTest {

    @Mock
    private PgPool pool;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JWTAuth jwtAuth;

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private RedisAPI redisApi;

    @Mock
    private UserExternalIdRepository userExternalIdRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @TempDir
    Path tempDir;

    @Test
    void uploadMyProfileImage_whenLevelIsLowerThan2_returnsBadRequest() throws Exception {
        UserService userService = createService();
        Path file = tempDir.resolve("low-level.png");
        writeImage(file, 600, 400, "png");

        when(userRepository.getUserById(pool, 39L))
            .thenReturn(Future.succeededFuture(User.builder().id(39L).level(1).build()));
        when(subscriptionService.getSubscriptionStatus(39L))
            .thenReturn(Future.succeededFuture(SubscriptionStatusResponseDto.builder()
                .profileImageUnlock(false)
                .build()));

        Throwable failure = awaitFailure(userService.uploadMyProfileImage(
            39L,
            file,
            "image/png",
            "low-level.png",
            Files.size(file)
        ));

        assertThat(failure).isInstanceOf(BadRequestException.class);
        assertThat(failure.getMessage()).contains("레벨 2 이상");
        verify(userRepository, never()).updateProfileImageUrl(any(), anyLong(), anyString());
    }

    @Test
    void uploadMyProfileImage_whenAllowed_updatesProfileImageUrl() throws Exception {
        UserService userService = createService();
        Path file = tempDir.resolve("allowed.png");
        writeImage(file, 800, 700, "png");

        when(userRepository.getUserById(pool, 39L))
            .thenReturn(Future.succeededFuture(User.builder().id(39L).level(2).build()));
        when(userRepository.updateProfileImageUrl(any(), eq(39L), anyString()))
            .thenAnswer(invocation -> Future.succeededFuture(
                User.builder()
                    .id(39L)
                    .level(2)
                    .profileImageUrl(invocation.getArgument(2))
                    .build()
            ));

        ProfileImageUploadResponseDto response = awaitSuccess(userService.uploadMyProfileImage(
            39L,
            file,
            "image/png",
            "allowed.png",
            Files.size(file)
        ));

        assertThat(response.getProfileImageUrl()).startsWith("/api/v1/users/profile-images/39/profile?v=");
        verify(userRepository).updateProfileImageUrl(any(), eq(39L), anyString());
    }

    @Test
    void uploadMyProfileImage_whenSubscriptionUnlockEnabled_updatesProfileImageUrl() throws Exception {
        UserService userService = createService();
        Path file = tempDir.resolve("vip-unlock.png");
        writeImage(file, 800, 700, "png");

        when(userRepository.getUserById(pool, 40L))
            .thenReturn(Future.succeededFuture(User.builder().id(40L).level(1).build()));
        when(subscriptionService.getSubscriptionStatus(40L))
            .thenReturn(Future.succeededFuture(SubscriptionStatusResponseDto.builder()
                .isSubscribed(true)
                .profileImageUnlock(true)
                .build()));
        when(userRepository.updateProfileImageUrl(any(), eq(40L), anyString()))
            .thenAnswer(invocation -> Future.succeededFuture(
                User.builder()
                    .id(40L)
                    .level(1)
                    .profileImageUrl(invocation.getArgument(2))
                    .build()
            ));

        ProfileImageUploadResponseDto response = awaitSuccess(userService.uploadMyProfileImage(
            40L,
            file,
            "image/png",
            "vip-unlock.png",
            Files.size(file)
        ));

        assertThat(response.getProfileImageUrl()).startsWith("/api/v1/users/profile-images/40/profile?v=");
        verify(userRepository).updateProfileImageUrl(any(), eq(40L), anyString());
    }

    @Test
    void deleteMyProfileImage_hardDeletesFiles_andClearsProfileImageUrl() throws Exception {
        UserService userService = createService();
        Path file = tempDir.resolve("delete-target.png");
        writeImage(file, 800, 800, "png");

        when(userRepository.getUserById(pool, 39L))
            .thenReturn(
                Future.succeededFuture(User.builder().id(39L).level(2).build()),
                Future.succeededFuture(User.builder().id(39L).level(2).profileImageUrl("/api/v1/users/profile-images/39/profile?v=1").build())
            );
        when(userRepository.updateProfileImageUrl(any(), eq(39L), any()))
            .thenAnswer(invocation -> Future.succeededFuture(
                User.builder()
                    .id(39L)
                    .level(2)
                    .profileImageUrl(invocation.getArgument(2))
                    .build()
            ));

        awaitSuccess(userService.uploadMyProfileImage(
            39L,
            file,
            "image/png",
            "delete-target.png",
            Files.size(file)
        ));
        Path userImageDir = tempDir.resolve("profile-storage").resolve("39");
        assertThat(Files.exists(userImageDir)).isTrue();

        awaitSuccess(userService.deleteMyProfileImage(39L));

        assertThat(Files.exists(userImageDir)).isFalse();
        verify(userRepository, times(2)).updateProfileImageUrl(any(), eq(39L), any());
    }

    private UserService createService() {
        return new UserService(
            pool,
            userRepository,
            jwtAuth,
            new JsonObject(),
            new JsonObject(),
            emailVerificationRepository,
            emailService,
            redisApi,
            userExternalIdRepository,
            subscriptionService,
            tempDir.resolve("profile-storage").toString(),
            new ProfileImageModerationService(null, false, null),
            new OfflinePayAttestationService(jwtAuth)
        );
    }

    private <T> T awaitSuccess(Future<T> future) throws Exception {
        return future.toCompletionStage()
            .toCompletableFuture()
            .get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
    }

    private Throwable awaitFailure(Future<?> future) throws Exception {
        try {
            future.toCompletionStage()
                .toCompletableFuture()
                .get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
            return fail("Expected future to fail");
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    private void writeImage(Path output, int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(120, 90, 220));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ImageIO.write(image, format, output.toFile());
    }
}
