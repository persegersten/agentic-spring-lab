FROM node:24-bookworm-slim AS frontend-build
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --include=dev --include=optional
COPY frontend/ ./
RUN npm run build

FROM nginx:1.29-alpine AS frontend-runtime
RUN sed -i 's#^pid .*#pid /tmp/nginx.pid;#' /etc/nginx/nginx.conf \
    && mkdir -p /tmp/nginx \
    && chown -R nginx:nginx /var/cache/nginx /tmp/nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend-build /build/frontend/dist/ /usr/share/nginx/html/
USER nginx:nginx

FROM eclipse-temurin:25-jdk AS backend-build
WORKDIR /build/backend
COPY backend/ ./
COPY --from=frontend-build /build/frontend/dist/ ./src/main/resources/static/
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre AS backend-runtime
WORKDIR /app
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 wreckage-app \
    && useradd --uid 10001 --gid 10001 --no-create-home --home-dir /app --shell /usr/sbin/nologin wreckage-app \
    && chown wreckage-app:wreckage-app /app
COPY --from=backend-build --chown=wreckage-app:wreckage-app /build/backend/target/wreckage-0.0.1-SNAPSHOT.jar ./app.jar
USER wreckage-app:wreckage-app
CMD ["java", "-jar", "/app/app.jar", "--server.port=8080", "--spring.profiles.active=in-memory"]
