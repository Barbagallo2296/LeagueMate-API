# LeagueMate API

> Backend REST per la gestione di tornei amatoriali di calcio a girone all'italiana.

---

## Tecnologie

| Stack | Versione |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.0 |
| Spring Security | 7.x |
| Spring Data JPA / Hibernate | 7.x |
| MySQL | 8.x |
| JWT (jjwt) | 0.12.6 |
| Lombok | 1.18.x |
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
├── controller/          # Endpoint REST
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
Il metodo `generateRounds()` in `TournamentServiceImpl` implementa l'algoritmo Round Robin di Berger per generare automaticamente le giornate di un girone all'italiana. Gestisce il numero dispari di squadre con un turno di riposo e alterna casa/trasferta ad ogni giornata.

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

Criteri di ordinamento: punti (decrescente) → differenza reti (decrescente).

### DTO — `StandingEntry` (Java Record)
```java
public record StandingEntry(
    String teamName, int points, int wins, int draws,
    int losses, int goalsFor, int goalsAgainst, int goalDifference
) {}
```

---

## Sicurezza

- Autenticazione **stateless** con JWT (nessun cookie di sessione)
- Token firmato con algoritmo **HS256**, scadenza 24h configurabile
- Filtro `JwtAuthFilter` intercetta ogni richiesta e valida il token
- Ruoli gestiti tramite `@PreAuthorize` abilitato con `@EnableMethodSecurity`
- Password hashate con **BCrypt**

### Endpoint pubblici
```
POST /api/auth/register
POST /api/auth/login
```

### Endpoint protetti
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
| `MethodArgumentNotValidException` | `400 Bad Request` |
| `Exception` (fallback) | `500 Internal Server Error` |

---

## Endpoint REST

### Auth
| Metodo | Endpoint | Accesso | Descrizione |
|---|---|---|---|
| POST | `/api/auth/register` | Pubblico | Registra un nuovo utente |
| POST | `/api/auth/login` | Pubblico | Login e generazione token JWT |

### Tornei
| Metodo | Endpoint | Accesso | Descrizione |
|---|---|---|---|
| POST | `/api/tournaments` | Autenticato | Crea un nuovo torneo |
| GET | `/api/tournaments` | Autenticato | Lista tutti i tornei |
| GET | `/api/tournaments/{id}` | Autenticato | Dettaglio torneo |
| GET | `/api/tournaments/status/{status}` | Autenticato | Filtra per stato |
| POST | `/api/tournaments/{id}/register-team/{teamId}` | Autenticato | Iscrive una squadra |
| POST | `/api/tournaments/{id}/generate-rounds` | Autenticato | Genera il calendario |
| GET | `/api/tournaments/{id}/standings` | Autenticato | Classifica dinamica |

### Squadre
| Metodo | Endpoint | Accesso | Descrizione |
|---|---|---|---|
| POST | `/api/teams` | Autenticato | Crea una squadra |
| GET | `/api/teams` | Autenticato | Lista tutte le squadre |
| GET | `/api/teams/{id}` | Autenticato | Dettaglio squadra |

### Partite
| Metodo | Endpoint | Accesso | Descrizione |
|---|---|---|---|
| PUT | `/api/matches/{id}/result` | Autenticato | Inserisce il risultato |
| GET | `/api/matches/round/{roundId}` | Autenticato | Partite di una giornata |

---

## Avvio in locale

### Prerequisiti
- Java 21, Maven 3.x, MySQL 8.x

### Configurazione `application.properties`
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/leaguemate_db?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=TUA_PASSWORD
jwt.secret=<chiave-Base64-min-32-caratteri>
jwt.expiration=86400000
```

### Avvio
```bash
mvn spring-boot:run
```

---

## Avvio con Docker

```bash
docker-compose up --build
```

Avvia automaticamente MySQL e l'applicazione Spring Boot con un solo comando.

---

## Testing

14 test unitari con JUnit 5 e Mockito — tutti verdi ✅

| Classe testata | Test |
|---|---|
| `UserServiceImpl` | 5 test (registrazione, duplicati, findByUsername) |
| `TournamentServiceImpl` | 3 test (creazione, recupero, not found) |
| `TeamServiceImpl` | 4 test (creazione, unicità nome, recupero) |
| `MatchServiceImpl` | 2 test (aggiornamento risultato, not found) |

---

## Stato del progetto

| Layer | Stato |
|---|---|
| Entity | ✅ Completo |
| Repository | ✅ Completo |
| Service | ✅ Completo |
| Security (JWT) | ✅ Completo |
| Controller | ✅ Completo |
| Docker | ✅ Completo |
| Test (JUnit 5) | ✅ Completo — 14/14 verdi |
| Script SQL | ✅ Completo |
| Postman Collection | ✅ Completo |
| Relazione tecnica | 🔜 Da fare |

---

## Autore

**Manuel Barbagallo**  
ITS Prodigi — Full Stack Developer (2025–2027)  
[GitHub](https://github.com/Barbagallo2296) | [LinkedIn](https://linkedin.com/in/manuel-barbagallo/)