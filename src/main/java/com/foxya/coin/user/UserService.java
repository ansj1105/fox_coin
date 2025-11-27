package com.foxya.coin.user;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.pgclient.PgPool;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.UnauthorizedException;
import com.foxya.coin.user.dto.CreateUserDto;
import com.foxya.coin.user.dto.LoginDto;
import com.foxya.coin.user.dto.LoginResponseDto;
import com.foxya.coin.user.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

@Slf4j
public class UserService extends BaseService {
    
    private final UserRepository userRepository;
    private final JWTAuth jwtAuth;
    private final JsonObject jwtConfig;
    
    public UserService(PgPool pool, UserRepository userRepository, JWTAuth jwtAuth, JsonObject jwtConfig) {
        super(pool);
        this.userRepository = userRepository;
        this.jwtAuth = jwtAuth;
        this.jwtConfig = jwtConfig;
    }
    
    public Future<User> createUser(CreateUserDto dto) {
        // 비밀번호 해시
        String passwordHash = BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt());
        dto.setPasswordHash(passwordHash);
        
        return userRepository.createUser(pool, dto);
    }
    
    public Future<LoginResponseDto> login(LoginDto dto) {
        return userRepository.getUserByLoginId(pool, dto.getLoginId())
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new UnauthorizedException("사용자를 찾을 수 없습니다."));
                }
                
                // 비밀번호 검증
                if (!BCrypt.checkpw(dto.getPassword(), user.getPasswordHash())) {
                    return Future.failedFuture(new UnauthorizedException("비밀번호가 일치하지 않습니다."));
                }
                
                // JWT 토큰 생성 (기본 USER 권한)
                String accessToken = com.foxya.coin.common.utils.AuthUtils.generateAccessToken(
                    jwtAuth,
                    user.getId(),
                    com.foxya.coin.common.enums.UserRole.USER
                );
                
                String refreshToken = com.foxya.coin.common.utils.AuthUtils.generateRefreshToken(
                    jwtAuth,
                    user.getId(),
                    com.foxya.coin.common.enums.UserRole.USER
                );
                
                return Future.succeededFuture(
                    LoginResponseDto.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .userId(user.getId())
                        .loginId(user.getLoginId())
                        .build()
                );
            });
    }
    
    public Future<User> getUserById(Long id) {
        return userRepository.getUserById(pool, id);
    }
}

