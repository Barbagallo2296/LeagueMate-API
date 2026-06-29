# LeagueMate API

> Backend REST per la gestione di tornei amatoriali di calcio a girone all'italiana.

---

## Tecnologie

| Stack | Versione |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.0 |
| Spring Security | 7.1.0 |
| Spring Data JPA | 2026.0.0 |
| MySQL | 8.x |
| JWT (jjwt) | 0.12.6 |
| Lombok | latest |
| Maven | 3.x |

---

## Architettura

L'applicazione segue una struttura MVC a tre layer rigorosi:

```
Controller → Service (interfaccia + impl) → Repository
```

```
src/main/java/com/leaguemate/api/
│
├── controller/          # Endpoint REST (in sviluppo)
├── service/             # Interfacce di business logic
│   └── impl/            # Implementazioni concrete
├── repository/          # Interfacce Spring Data JPA
├── entity/              # Entity JPA mappate su MySQL
├── dto/                 # Java Records come DTO
├── security/            # JWT Filter, UserDetails, SecurityConfig
└── exception/           # Eccezioni custom + GlobalExceptionHandler
```

---

## Modello del Dominio

### Relazioni JPA implementate

| Tipo | Entità |
|---|---|
| `@OneToOne` | `User` ↔ `UserProfile` |
| `@OneToMany / @ManyToOne` | `Tournament` → `Round` → `Match` |
| `@ManyToMany` (ricca) | `User` ↔ `Team` tramite `TeamMember` |
| `@ManyToMany` (ricca) | `Tournament` ↔ `Team` tramite `TournamentRegistration` |

### Entity

- **User** — implementa `UserDetails`, autenticazione via username, ruoli enum (`ADMIN`, `ORGANIZER`, `USER`)
- **UserProfile** — dati aggiuntivi collegati all'utente (bio, avatar, telefono)
- **Team** — squadra con membri e iscrizioni ai tornei
- **TeamMember** — giunzione ricca User↔Team con `TeamRole` (CAPTAIN, PLAYER, RESERVE) e `joinedAt`
- **Tournament** — torneo con stagione, stato e configurazione punti (vittoria/pareggio)
- **TournamentRegistration** — giunzione ricca Tournament↔Team con `RegistrationStatus` e `registeredAt`
- **Round** — giornata del torneo, ordinata per `roundNumber`
- **Match** — partita con squadra casa/trasferta, score e stato (SCHEDULED/COMPLETED)

---

## Funzionalità principali

### Generazione calendario (Algoritmo di Berger)
Il metodo `generateRounds()` in `TournamentServiceImpl` implementa l'algoritmo Round Robin di Berger per generare automaticamente le giornate di un girone all'italiana. Gestisce il numero dispari di squadre con un turno di riposo (`null` team) e alterna casa/trasferta ad ogni giornata.

### Calcolo classifica dinamico (Stream API)
Il metodo `calculateStandings()` calcola la classifica in tempo reale leggendo le partite con stato `COMPLETED` tramite Stream API:

```java
matchRepository.findByRoundTournamentIdAndStatus(tournamentId, MatchStatus.COMPLETED)
    .forEach(match -> { /* aggiorna statistiche */ });

return statsMap.entrySet().stream()
    .map(entry -> new StandingEntry(...))
    .sorted(Comparator.comparingInt(StandingEntry::points).reversed()
        .thenComparingInt(StandingEntry::goalDifference).reversed())
    .collect(Collectors.toList());
```

Criteri di ordinamento:
1. Punti (decrescente)
2. Differenza reti (decrescente)

### DTO — `StandingEntry` (Java Record)
```java
public record StandingEntry(
    String teamName,
    int points,
    int wins,
    int draws,
    int losses,
    int goalsFor,
    int goalsAgainst,
    int goalDifference
) {}
```

---

## Sicurezza

- Autenticazione **stateless** con JWT (nessun cookie di sessione)
- Token firmato con algoritmo **HS256**
- Scadenza configurabile via `application.properties`
- Filtro `JwtAuthFilter` intercetta ogni richiesta e valida il token
- Ruoli gestiti tramite `@PreAuthorize` abilitato con `@EnableMethodSecurity`
- Password hashate con **BCrypt**

### Endpoint pubblici
```
POST /api/auth/register
POST /api/auth/login
```

### Endpoint protetti
Tutti gli endpoint sotto `/api/**` richiedono un token JWT valido nell'header:
```
Authorization: Bearer <token>
```

---

## Gestione Errori

Gestione centralizzata tramite `@RestControllerAdvice`:

| Eccezione | HTTP Status |
|---|---|
| `ResourceNotFoundException` | `404 Not Found` |
| `ResourceConflictException` | `409 Conflict` |
| `Exception` (fallback) | `500 Internal Server Error` |

---

## Avvio in locale

### Prerequisiti
- Java 21
- Maven 3.x
- MySQL 8.x

### Configurazione database
Crea un database MySQL chiamato `leaguemate_db` oppure lascia che Spring lo crei automaticamente grazie a `createDatabaseIfNotExist=true` nell'URL.

### Variabili in `application.properties`
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/leaguemate_db?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=root
jwt.secret=<chiave-segreta-min-32-caratteri>
jwt.expiration=86400000
```

### Avvio
```bash
mvn spring-boot:run
```

---

## Stato del progetto

| Layer | Stato |
|---|---|
| Entity | ✅ Completo |
| Repository | ✅ Completo |
| Service | ✅ Completo |
| Security (JWT) | ✅ Completo (Core & Servizi pronti) |
| Controller | 🔜 Prossimo step |
| Docker | 🔜 Da fare |
| Test (JUnit 5) | 🔜 Da fare |
| Documentazione Postman | 🔜 Da fare |

---

## Autore

**Manuel Barbagallo**  
ITS Prodigi — Full Stack Developer (2025–2027)  
[GitHub](https://github.com/Barbagallo2296)
