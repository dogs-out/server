# Dogs Out – Server

## Project Overview
Dogs Out is a mobile social platform for dog owners to discover and meet other dog owners nearby. Think "Tinder for Dogs". This is the backend server for the Dogs Out mobile app.

## Tech Stack
- **Language:** Java 21
- **Framework:** Spring Boot 3.5.x
- **Database:** PostgreSQL (local) / Railway (production)
- **ORM:** Hibernate / Spring Data JPA
- **Security:** Spring Security, JWT, Google OAuth 2.0
- **Testing:** JUnit 5, Mockito, jqwik (property-based), JaCoCo
- **Build:** Maven
- **CI/CD:** GitHub Actions + SonarCloud

## Architecture
Modular monolith — one Spring Boot app, organised by domain module.

### Package Structure
```
com.dogsout.server/
├── auth/
│   ├── AuthController.java
│   ├── AuthService.java
│   └── AuthRepository.java
├── user/
│   ├── User.java
│   ├── UserController.java
│   ├── UserService.java
│   └── UserRepository.java
├── dog/
│   ├── Dog.java
│   ├── DogController.java
│   ├── DogService.java
│   └── DogRepository.java
├── matching/
├── chat/
├── notification/
└── ServerApplication.java
```

### Layer Responsibilities
- **Controller** — REST API endpoints, request/response handling
- **Service** — business logic
- **Repository** — database access (Spring Data JPA)
- **Entity** — JPA entity, maps to PostgreSQL table

## Database
- Local: PostgreSQL on localhost:5432/dogsout
- Username: moritzdavinghausen
- Password: (none)
- `ddl-auto=update` — Hibernate auto-creates/updates tables from entities

## Domain Entities

### User
id, name, email, password, dateOfBirth, bio, profilePicture (URL),
latitude, longitude, role (USER/ADMIN), authProvider (LOCAL/GOOGLE),
isActive, createdAt, updatedAt

### Dog
id, name, breed, age, bio, profilePicture (URL), owner (User), createdAt

### Match
id, user1 (User), user2 (User), status (PENDING/MATCHED/REJECTED), createdAt

### Message
id, sender (User), receiver (User), match (Match), content, sentAt, isRead

## Coding Conventions
- Use Lombok (@Data, @Builder, @NoArgsConstructor, @AllArgsConstructor) to reduce boilerplate
- DTOs for request/response (never expose entities directly via API)
- Services are @Transactional
- All endpoints return ResponseEntity<>
- Passwords must be BCrypt hashed, never stored in plain text
- Use @Valid for request validation

## Running Locally
```bash
mvn spring-boot:run
```
Server runs on http://localhost:8080

## Testing
```bash
mvn test
```
- Unit tests: JUnit 5 + Mockito
- Property-based: jqwik (property-based testing)
- Coverage: JaCoCo (target ≥ 80%)

## Environment Variables (never commit these)
- GOOGLE_CLIENT_ID
- GOOGLE_CLIENT_SECRET
- JWT_SECRET

## Current Status
- [x] Spring Boot project initialised
- [x] PostgreSQL connected
- [x] GitHub Actions CI with SonarCloud
- [ ] User entity
- [ ] Auth (JWT + Google OAuth)
- [ ] Dog entity
- [ ] Matching engine
- [ ] Chat (WebSockets)
- [ ] Push notifications (Firebase)
- [ ] Admin dashboard
- [ ] Deployment (Railway)

## Internship Context
This is a 12-week UZH Informatikpraktikum project (Jun 15 – Sep 4, 2026).
Supervisor: Lea Mutschler (weekly Tuesday meetings via Microsoft Teams).
