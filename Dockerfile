FROM node:24-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --include=dev
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk AS backend
WORKDIR /build/backend
COPY backend/ ./
COPY --from=frontend /build/frontend/dist/ ./src/main/resources/static/
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=backend /build/backend/target/wreckage-0.0.1-SNAPSHOT.jar ./app.jar
USER 1000
CMD ["sh", "-c", "exec java -jar /app/app.jar --server.port=${PORT:-8080} --spring.profiles.active=in-memory"]
