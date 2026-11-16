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
gradlew.bat bootRun
```

## API Docs
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI: http://localhost:8080/v3/api-docs

## Test
```bash
./gradlew test
```

## Build
```bash
./gradlew build
```

## Exam schedule APIs
- Admin schedule and room management uses `/api/admin/exam-schedules` and `/api/admin/exam-rooms`.
- Schedule filters include `keyword`, `semesterId`, `courseId`, `status`, `fromDate`, and `toDate`.
- Admin Excel exports are available at `/api/admin/exam-schedules/export` and `/api/admin/exam-schedules/{id}/students/export`.
- Student schedule and Excel endpoints are `/api/student/exam-schedules` and `/api/student/exam-schedules/export`; the backend derives the student from the authenticated JWT.
- All exam management endpoints require an authenticated account with the matching ADMIN or STUDENT role.
