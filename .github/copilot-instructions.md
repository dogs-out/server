# Dogs Out – Server Copilot Instructions

## Project
Spring Boot 3.5.x REST API backend for Dogs Out, a mobile social platform for dog owners. Java 21, PostgreSQL, Spring Security, JWT auth, Hibernate/JPA.

## Architecture
Modular monolith. Each domain module (user, dog, matching, chat, auth, notification) contains its own Controller, Service, Repository, and Entity.

## Build & Run
- Build: `mvn clean install`
- Run: `mvn spring-boot:run` (requires PostgreSQL on localhost:5432/dogsout)
- Test: `mvn test` (uses H2 in-memory for tests)

## Conventions
- Use Lombok (@Data, @Builder, @NoArgsConstructor, @AllArgsConstructor)
- Never expose entities directly — always use DTOs
- Services must be @Transactional
- All endpoints return ResponseEntity<>
- Passwords must be BCrypt hashed
- Use @Valid for all request bodies
- Never commit secrets — use environment variables

## Testing
- JUnit 5 + Mockito for unit and integration tests
- jqwik for property-based testing
- JaCoCo for coverage (target ≥ 80%)
- Test resources use H2 in-memory database

## CI
GitHub Actions runs `mvn verify sonar:sonar` on every push and PR to main.