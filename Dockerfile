# Runtime image of the msg CX PPAP Connector (same image for every participant, configured via env).
# Build the jar first (`make build`: frontend + backend), then `docker build -t ppap-connector:local .`
# The image build itself needs no network access besides pulling the base image.
FROM eclipse-temurin:25-jre-noble AS layers
WORKDIR /build
COPY backend/app/target/ppap-connector.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre-noble
RUN groupadd --system --gid 10001 ppap && useradd --system --uid 10001 --gid ppap ppap
WORKDIR /app
COPY --from=layers /build/extracted/dependencies/ ./
COPY --from=layers /build/extracted/spring-boot-loader/ ./
COPY --from=layers /build/extracted/snapshot-dependencies/ ./
COPY --from=layers /build/extracted/application/ ./
USER 10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
