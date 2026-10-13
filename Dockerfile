FROM node:24-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-25 AS backend
WORKDIR /build
COPY backend/ ./backend/
COPY --from=frontend /build/frontend/dist ./frontend/dist/
RUN mvn -f backend/pom.xml -B package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=backend /build/backend/target/tabletop-0.1.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
