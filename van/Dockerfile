# 1단계: 빌드
FROM amazoncorretto:17-alpine AS build
WORKDIR /app
# Gradle Wrapper와 소스 코드 복사
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
COPY src ./src
# 실행 권한 부여 및 빌드
RUN chmod +x ./gradlew
RUN ./gradlew clean build -x test --no-daemon

# 2단계: 실행
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# 빌드 결과물을 가져오기
COPY --from=builder /app/build/libs/*.jar app.jar
# 실행
ENTRYPOINT ["java", "-jar", "app.jar"]