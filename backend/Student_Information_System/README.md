# Student Information System (Backend)

## Tech Stack
- Java 21, Spring Boot 3.3.x, Spring Security, Spring Data JPA
- PostgreSQL
- JWT Authentication
- OpenAPI/Swagger

## Configuration
Copy `.env` example or set environment variables:
- `DB_URL` (default: jdbc:postgresql://localhost:5432/sis_db)
- `DB_USERNAME` (default: postgres)
- `DB_PASSWORD` (default: postgres)
- `JWT_SECRET` (change in production)

## Run
```bash
./gradlew bootRun
```

## API Docs
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI: http://localhost:8080/api-docs

## Test
```bash
./gradlew test
```

## Build
```bash
./gradlew build
```
