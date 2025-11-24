FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 타임존 설정
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone

# fat jar 복사
COPY build/libs/*-fat.jar app.jar

# 설정 파일 복사
COPY src/main/resources/config.json /app/config.json

# 로깅 설정
COPY src/main/resources/log4j2.xml /app/log4j2.xml

# 포트 노출
EXPOSE 8080

# JVM 옵션 설정
ENV JAVA_OPTS="-Xmx1024m -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 애플리케이션 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

