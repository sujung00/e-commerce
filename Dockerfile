# Java 17 런타임 환경
FROM eclipse-temurin:17-jre-jammy

# 작업 디렉토리 설정
WORKDIR /app

# 로컬에서 빌드된 jar 파일을 컨테이너로 복사
# Gradle 빌드 결과물 위치: build/libs/*.jar
COPY build/libs/*.jar app.jar

# Spring Boot 기본 포트 노출
EXPOSE 8080

# 애플리케이션 실행
# -Djava.security.egd=file:/dev/./urandom: 빠른 시작을 위한 랜덤 소스 설정
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]