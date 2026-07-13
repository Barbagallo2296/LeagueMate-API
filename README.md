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
| JaCoCo | 0.8.12 |
| Maven | 3.x |

---

## Architettura

Struttura MVC a tre layer rigorosi:

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
├── dto/                 # Java Records come DTO (input e output)
├── security/            # JWT Filter, UserDetails, SecurityConfig
└── exception/           # Eccezioni custom + GlobalExceptionHandler
```

### Moduli funzionali

| Modulo | Controller | Service | Descrizione |
|---|---|---|---|
| **Auth** | `AuthController` | `AuthService` | Registrazione e login con JWT |
| **Tournament** | `TournamentController` | `TournamentService` | CRUD tornei, iscrizioni, calendario, classifica, statistiche, co-organizzatori |
| **Team** | `TeamController` | `TeamService` | CRUD squadre |
| **TeamMember** | `TeamMemberController` | `TeamMemberService` | Membri delle squadre con ruoli |
| **Match** | `MatchController` | `MatchService` | Risultati delle partite |

> **Nessuna Entity JPA viene esposta nelle risposte API.** Tutti i controller restituiscono esclusivamente DTO (Java Records), isolando completamente il modello di persistenza dal contratto REST.

---

## Data Layer

### Relazioni JPA — tutte e quattro le tipologie

| Tipo | Entità | Note |
|---|---|---|
| `@OneToOne` | `User` ↔ `UserProfile` | FK su `UserProfile` |
| `@ManyToOne / @OneToMany` | `Tournament` → `Round` → `Match` | Tutte LAZY |
| `@ManyToMany` **pura** | `Tournament` ↔ `User` (co-organizzatori) | `@JoinTable` su `tournament_organizers` — la relazione non porta attributi propri |
| `@ManyToMany` **con giunzione ricca** | `User` ↔ `Team` tramite `TeamMember` | Attributi: `teamRole`, `joinedAt` |
| `@ManyToMany` **con giunzione ricca** | `Tournament` ↔ `Team` tramite `TournamentRegistration` | Attributi: `status`, `registeredAt` |

**Scelta progettuale:** dove la relazione N:N porta attributi propri si usa un'entità di giunzione (modellazione corretta); dove non ne porta si usa `@ManyToMany` pura con `@JoinTable`.

### Elementi avanzati JPA

| Elemento | Dove | Perché |
|---|---|---|
| `FetchType.LAZY` | Tutte le relazioni `@ManyToOne` e `@ManyToMany` | Evita query non necessarie |
| **JPQL con `@Query` + `@Param`** | `MatchRepository`, `TournamentRepository`, `TournamentRegistrationRepository` | Query esplicite e type-safe |
| **`JOIN FETCH`** | `findCompletedMatchesWithTeams()`, `findByRoundIdWithTeams()`, `findConfirmedWithTeams()` | **Risolve un problema N+1 reale** nel calcolo della classifica |
| **Query di aggregazione (`COUNT`)** | `countMatchesByTournamentAndStatus()`, `countConfirmedTeams()` | Statistiche del torneo |
| **JOIN su `@ManyToMany`** | `findTournamentsByOrganizerId()` | Naviga la relazione N:N in JPQL |
| **`@EntityGraph`** | `findWithRegistrationsById()` | Fetch dichiarativo senza JPQL |

### Entity

- **User** — implementa `UserDetails`, ruoli enum (`ADMIN`, `ORGANIZER`, `USER`)
- **UserProfile** — dati aggiuntivi (bio, avatar, telefono)
- **Team** — squadra con membri e iscrizioni
- **TeamMember** — giunzione ricca User↔Team con `TeamRole` (CAPTAIN/PLAYER/RESERVE) e `joinedAt`
- **Tournament** — torneo con stagione, stato, configurazione punti e **co-organizzatori**
- **TournamentRegistration** — giunzione ricca Tournament↔Team con `RegistrationStatus` e `registeredAt`
- **Round** — giornata del torneo
- **Match** — partita con squadra casa/trasferta, score e stato

---

## Funzionalità principali

### Generazione calendario (Algoritmo di Berger)
`generateRounds()` implementa l'algoritmo Round Robin di Berger. Con N squadre genera N-1 giornate da N/2 partite. Gestisce il numero dispari con un turno di riposo e alterna casa/trasferta.

### Calcolo classifica dinamico (Stream API + JOIN FETCH)
`calculateStandings()` calcola la classifica in tempo reale dalle partite `COMPLETED`, senza persisterla (nessun rischio di disallineamento). Usa JPQL con `JOIN FETCH` per caricare squadre e partite in un'unica query.

```java
return statsMap.entrySet().stream()
    .map(entry -> new StandingEntry(...))
    .sorted(Comparator.comparingInt(StandingEntry::points).reversed()
        .thenComparing(Comparator.comparingInt(StandingEntry::goalDifference).reversed()))
    .collect(Collectors.toList());
```

### Statistiche del torneo
`getTournamentStats()` aggrega dati con query `COUNT` JPQL: squadre iscritte, partite giocate/rimanenti, gol totali, media gol a partita, miglior attacco.

### Validazioni di dominio
- Le squadre possono essere iscritte **solo a tornei in stato `DRAFT`**
- Un torneo `COMPLETED` non può essere modificato
- Un torneo `ACTIVE` non può essere eliminato
- Una squadra iscritta a un torneo non può essere eliminata

---

## Sicurezza

- Autenticazione **stateless** con JWT (HS256, scadenza 24h)
- Filtro `JwtAuthFilter` valida il token ad ogni richiesta
- Password hashate con **BCrypt**
- Autorizzazione per ruoli (RBAC) tramite `@PreAuthorize` e `@EnableMethodSecurity`
- Handler dedicato per `AccessDeniedException` → `403 Forbidden`

---

## Gestione Errori

`@RestControllerAdvice` centralizzato:

| Eccezione | HTTP Status |
|---|---|
| `MethodArgumentNotValidException` | `400 Bad Request` |
| `AccessDeniedException` | `403 Forbidden` |
| `ResourceNotFoundException` | `404 Not Found` |
| `ResourceConflictException` | `409 Conflict` |
| `Exception` (fallback) | `500 Internal Server Error` |

---

## Endpoint REST — 25 totali

### Auth (2)
| Metodo | Endpoint | Accesso |
|---|---|---|
| POST | `/api/auth/register` | Pubblico |
| POST | `/api/auth/login` | Pubblico |

### Tornei (10)
| Metodo | Endpoint | Accesso |
|---|---|---|
| POST | `/api/tournaments` | **ADMIN / ORGANIZER** |
| GET | `/api/tournaments` | Autenticato |
| GET | `/api/tournaments/{id}` | Autenticato |
| GET | `/api/tournaments/status/{status}` | Autenticato |
| PUT | `/api/tournaments/{id}` | **ADMIN / ORGANIZER** |
| DELETE | `/api/tournaments/{id}` | **ADMIN** |
| POST | `/api/tournaments/{id}/register-team/{teamId}` | **ADMIN / ORGANIZER** |
| POST | `/api/tournaments/{id}/generate-rounds` | **ADMIN / ORGANIZER** |
| GET | `/api/tournaments/{id}/standings` | Autenticato |
| GET | `/api/tournaments/{id}/stats` | Autenticato |

### Co-organizzatori — `@ManyToMany` (3)
| Metodo | Endpoint | Accesso |
|---|---|---|
| POST | `/api/tournaments/{id}/organizers/{userId}` | **ADMIN / ORGANIZER** |
| GET | `/api/tournaments/{id}/organizers` | Autenticato |
| DELETE | `/api/tournaments/{id}/organizers/{userId}` | **ADMIN / ORGANIZER** |

### Squadre (5)
| Metodo | Endpoint | Accesso |
|---|---|---|
| POST | `/api/teams` | Autenticato |
| GET | `/api/teams` | Autenticato |
| GET | `/api/teams/{id}` | Autenticato |
| PUT | `/api/teams/{id}` | **ADMIN / ORGANIZER** |
| DELETE | `/api/teams/{id}` | **ADMIN** |

### Membri delle squadre (3)
| Metodo | Endpoint | Accesso |
|---|---|---|
| POST | `/api/teams/{teamId}/members` | **ADMIN / ORGANIZER** |
| GET | `/api/teams/{teamId}/members` | Autenticato |
| DELETE | `/api/teams/{teamId}/members/{memberId}` | **ADMIN / ORGANIZER** |

### Partite (2)
| Metodo | Endpoint | Accesso |
|---|---|---|
| PUT | `/api/matches/{id}/result` | **ADMIN / ORGANIZER** |
| GET | `/api/matches/round/{roundId}` | Autenticato |

---

## Avvio con Docker (consigliato)

```bash
docker-compose up --build
```

Un solo comando avvia MySQL 8 e l'applicazione. Il Dockerfile usa un multi-stage build (Maven → JRE Alpine). MySQL ha un healthcheck e l'app attende che sia pronto.

---

## Avvio in locale

### Prerequisiti
Java 21, Maven 3.x, MySQL 8.x

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/leaguemate_db?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=TUA_PASSWORD
jwt.secret=<chiave-Base64-min-32-caratteri>
jwt.expiration=86400000
```

```bash
mvn spring-boot:run
```

---

## Testing

**52 test** con JUnit 5, Mockito e Spring Security Test — tutti verdi ✅
**Code coverage: 69%** (requisito minimo 35%)

| Classe testata | Test | Descrizione |
|---|---|---|
| `TournamentServiceImpl` | 16 | CRUD, **algoritmo di Berger**, **classifica**, **statistiche**, co-organizzatori |
| `TeamServiceImpl` | 9 | CRUD completo, unicità nome, vincoli di cancellazione |
| `TeamMemberServiceImpl` | 6 | Aggiunta membri, duplicati, rimozione |
| `UserServiceImpl` | 5 | Registrazione, duplicati, ricerca |
| `JwtService` | 4 | Generazione, estrazione, validazione token |
| `GlobalExceptionHandler` | 4 | 400, 404, 409, 500 |
| `AuthServiceImpl` | 3 | Registrazione con hashing, login |
| `TournamentControllerSecurityTest` | 3 | **403 con USER, 201 con ORGANIZER** (`@WebMvcTest`) |
| `MatchServiceImpl` | 2 | Aggiornamento risultato |

### Coverage per package

| Package | Coverage |
|---|---|
| `exception` | 100% |
| `service.impl` | 83% |
| `security` | 69% |
| `controller` | 14% |
| **Totale** | **69%** |

> `dto` ed `entity` sono esclusi (boilerplate Lombok). I controller sono **inclusi**.

### Bug individuati dai test

1. **Ordinamento della classifica** — `.thenComparingInt(...).reversed()` invertiva l'intero comparatore concatenato, producendo una classifica rovesciata.
2. **Status code su accesso negato** — l'handler generico su `Exception.class` intercettava `AccessDeniedException`, trasformando un legittimo `403` in un `500`.

```bash
mvn clean test
```
Report JaCoCo in `target/site/jacoco/index.html`.

---

## Deliverables

| Elemento | Stato |
|---|---|
| Codice sorgente completo | ✅ |
| Script SQL (`schema.sql` + `data.sql`) | ✅ |
| Collection Postman (25 endpoint, 6 cartelle) | ✅ |
| Script Docker (`Dockerfile` + `docker-compose.yml`) | ✅ |
| Relazione tecnica | ✅ |

---

## Autore

**Manuel Barbagallo**  
ITS Prodigi — Full Stack Developer (2025–2027)  
[GitHub](https://github.com/Barbagallo2296) | [LinkedIn](https://linkedin.com/in/manuel-barbagallo/)