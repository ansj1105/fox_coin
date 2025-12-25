# Foxya Coin Service

Vert.x 기반의 고성능 암호화폐 지갑 & 코인 관리 시스템

## ️ 프로젝트 구조
```
src/main/java/com/foxya/coin/
├── MainVerticle.java              # 메인 진입점
├── verticle/                      # Verticles
│   └── ApiVerticle.java          # HTTP API 서버
├── common/                        # 공통 모듈
│   ├── BaseHandler.java          # Handler 베이스 (evcloud 스타일)
│   ├── BaseService.java          # Service 베이스
│   ├── BaseRepository.java       # Repository 베이스 (RowMapper 패턴)
│   ├── database/
│   │   ├── ParametersMapped.java # DTO toMap() interface
│   │   └── RowMapper.java        # Row 매핑 인터페이스
│   ├── dto/
│   │   └── ApiResponse.java
│   ├── exceptions/
│   │   ├── BadRequestException.java
│   │   ├── NotFoundException.java
│   │   └── UnauthorizedException.java
│   └── utils/
│       └── ErrorHandler.java
├── utils/                         # QueryBuilder (evcloud 복사)
│   ├── QueryBuilder.java         # 쿼리 빌더 (#{param} 방식)
│   └── BaseQueryBuilder.java    # 베이스 쿼리 빌더
├── config/
│   └── ConfigLoader.java         # 설정 로더
├── user/                         # 사용자 도메인
│   ├── UserHandler.java
│   ├── UserService.java
│   ├── UserRepository.java
│   ├── dto/
│   └── entity/
├── wallet/                       # 지갑 도메인
├── transaction/                  # 트랜잭션 도메인
└── currency/                     # 통화 도메인
```

##  주요 특징

- **Lombok 적극 활용**: @Data, @Builder, @Getter 등
- **QueryBuilder 패턴**: #{param} 방식의 동적 쿼리
- **RowMapper 패턴**: 타입 안전한 Row 매핑
- **ParametersMapped**: DTO의 toMap() 인터페이스

##  시작하기

### 1. Gradle Wrapper 생성
```bash
gradle wrapper --gradle-version 8.5
```

### 2. 빌드
```bash
./gradlew clean build
```

### 3. 실행
```bash
./gradlew run
```

##  사용 예시

### Entity (Lombok 사용)
```java
@Getter
@AllArgsConstructor
@Builder
public class User {
    private Long id;
    private String username;
    private String email;
    private LocalDateTime createdAt;
}
```

### DTO (toMap() 구현)
```java
@Getter
@Setter
@Builder
public class CreateUserDto implements ParametersMapped {
    private String username;
    private String email;
    
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> params = new HashMap<>();
        if (username != null) params.put("username", username);
        if (email != null) params.put("email", email);
        return params;
    }
}
```

### Repository (RowMapper 패턴)
```java
public class UserRepository extends BaseRepository {
    
    private final RowMapper<User> userMapper = row -> User.builder()
        .id(getLongColumnValue(row, "id"))
        .username(getStringColumnValue(row, "username"))
        .email(getStringColumnValue(row, "email"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .build();
    
    public Future<User> getUserById(SqlClient client, Long id) {
        String sql = QueryBuilder
            .select("users")
            .whereById()
            .build();
        
        return query(client, sql, Collections.singletonMap("id", id))
            .map(rows -> fetchOne(userMapper, rows));
    }
}
```

### Handler (BaseHandler 사용)
```java
public class UserHandler extends BaseHandler {
    
    private final UserService userService;
    
    public UserHandler(Vertx vertx, UserService userService) {
        super(vertx);
        this.userService = userService;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(vertx);
        router.get("/:id").handler(this::getUser);
        return router;
    }
    
    private void getUser(RoutingContext ctx) {
        Long id = Long.valueOf(ctx.pathParam("id"));
        response(ctx, userService.getUser(id));
    }
}
```

## ️ 기술 스택

- **Vert.x**: 고성능 비동기 서버
- **PostgreSQL**: 메인 데이터베이스
- **Gradle**: 빌드 도구
- **Lombok**: 보일러플레이트 제거
- **Log4j2**: 로깅
- **JWT**: 인증

##  라이센스

MIT
