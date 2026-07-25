# LeagueMate API

Backend REST per la gestione di tornei amatoriali di calcio a girone all'italiana.


## Tecnologie

| Stack | Versione |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.0 |
| Spring Security | 7.x |
| Spring Data JPA / Hibernate | 7.x |
| MySQL | 8.x |
| H2 (solo test) | in memoria |
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
│   └── impl/            # Implementazioni 
├── repository/          # Interfacce Spring Data JPA
├── entity/              # Entity JPA su MySQL
├── dto/                 # Java Records (input/output)
├── security/            # JWT Filter e SecurityConfig
└── exception/           # Handler eccezioni
```

### Moduli funzionali

| Modulo | Controller | Service | Descrizione |
|---|---|---|---|
| **Auth** | `AuthController` | `AuthService` | Registrazione e login con JWT |
| **User** | `UserController` | `UserService` | Gestione utenti, ruoli e profilo (`@OneToOne`) |
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
| `@OneToOne` | `User` ↔ `UserProfile` | FK su `UserProfile`, esposta via `/api/users/{id}/profile` |
| `@ManyToOne / @OneToMany` | `Tournament` → `Round` → `Match` | Tutte LAZY |
| `@ManyToMany`  | `Tournament` ↔ `User` (co-organizzatori) | `@JoinTable` su `tournament_organizers` — la relazione non porta attributi propri |
| `@ManyToMany`| `User` ↔ `Team` tramite `TeamMember` | Attributi: `teamRole`, `joinedAt` |
| `@ManyToMany`| `Tournament` ↔ `Team` tramite `TournamentRegistration` | Attributi: `status`, `registeredAt` |

**Scelta progettuale:** dove la relazione N:N porta attributi propri si usa un'entità di giunzione (modellazione corretta); dove non ne porta si usa `@ManyToMany` pura con `@JoinTable`.

### Elementi avanzati JPA

| Elemento | Dove | Perché |
|---|---|---|
| `FetchType.LAZY` | Tutte le relazioni `@ManyToOne` e `@ManyToMany` | Evita query non necessarie |
| **JPQL con `@Query` + `@Param`** | `MatchRepository`, `TournamentRepository`, `TournamentRegistrationRepository`, `TeamMemberRepository` | Query esplicite e type-safe |
| **`JOIN FETCH`** | `findCompletedMatchesWithTeams()`, `findByRoundIdWithTeams()`, `findConfirmedWithTeams()`, `findByIdWithTeams()`, `findByTeamIdWithUserAndTeam()` | **Risolve problemi N+1 reali** nel calcolo della classifica e nell'elenco dei membri |
| **Query di aggregazione (`COUNT`)** | `countMatchesByTournamentAndStatus()`, `countConfirmedTeams()`, `countByRole()` | Statistiche e regole di dominio |
| **Query derivate dal nome** | `existsByTeamIdAndUserId()`, `existsByUsername()`, `countByRole()` | Controlli senza caricare intere tabelle |
| **`@EntityGraph`** | `findWithRegistrationsById()` | Fetch dichiarativo, usato nell'iscrizione delle squadre |

### Configurazione JPA

`spring.jpa.open-in-view=false` — disattivato di proposito. La sessione Hibernate non resta aperta durante la serializzazione della risposta: ogni endpoint carica esplicitamente le associazioni necessarie tramite `JOIN FETCH` o `@EntityGraph`, evitando accessi lazy nascosti fuori dalla transazione.

### Entity

- **User** — implementa `UserDetails`, ruoli enum (`ADMIN`, `ORGANIZER`, `USER`)
- **UserProfile** — dati aggiuntivi (bio, avatar, telefono), relazione `@OneToOne`
- **Team** — squadra con membri e iscrizioni
- **TeamMember** — giunzione ricca User↔Team con `TeamRole` (CAPTAIN/PLAYER/RESERVE) e `joinedAt`
- **Tournament** — torneo con stagione, stato, configurazione punti e **co-organizzatori**
- **TournamentRegistration** — giunzione ricca Tournament↔Team con `RegistrationStatus` e `registeredAt`
- **Round** — giornata del torneo
- **Match** — partita con squadra casa/trasferta, score e stato

---

## Funzionalità principali

### Generazione calendario (round-robin, metodo del cerchio)
`generateRounds()` genera il calendario all'italiana con il **metodo del cerchio** (circle method), che produce lo stesso calendario delle tabelle di Berger: una squadra resta fissa e le altre ruotano attorno a essa. Con N squadre genera N-1 giornate da N/2 partite, e ogni coppia si incontra esattamente una volta. Gestisce il numero dispari con un turno di riposo e alterna casa/trasferta fra le giornate. La generazione è consentita **solo in stato `DRAFT`**, per non cancellare risultati già registrati.

### Calcolo classifica dinamico (Stream API + JOIN FETCH)
`calculateStandings()` calcola la classifica in tempo reale dalle partite `COMPLETED`, senza persisterla: non può mai andare fuori sincrono con i risultati. Usa un accumulatore tipizzato `TeamStats` (niente indici magici) e ordina per punti, differenza reti, gol fatti e nome.

```java
return table.values().stream()
    .map(TeamStats::toEntry)
    .sorted(Comparator.comparingInt(StandingEntry::points).reversed()
        .thenComparing(Comparator.comparingInt(StandingEntry::goalDifference).reversed())
        .thenComparing(Comparator.comparingInt(StandingEntry::goalsFor).reversed())
        .thenComparing(StandingEntry::teamName))
    .toList();
```

### Statistiche del torneo
`getTournamentStats()` aggrega dati con query `COUNT` JPQL: squadre iscritte, partite giocate/rimanenti, gol totali, media gol a partita, miglior attacco.

### Validazioni di dominio
- Le squadre possono essere iscritte **solo a tornei in stato `DRAFT`**
- Il calendario può essere generato **solo in stato `DRAFT`**
- Un torneo `COMPLETED` non può essere modificato
- Un torneo `ACTIVE` non può essere eliminato
- Non è possibile declassare l'ultimo `ADMIN` rimasto

---

## Sicurezza

- Autenticazione **stateless** con JWT (HS256, scadenza 24h)
- Filtro `JwtAuthFilter` valida il token ad ogni richiesta e gestisce token scaduti/malformati restituendo `401` in formato JSON
- Password hashate con **BCrypt**
- Autorizzazione **per ruolo** (RBAC) tramite `@PreAuthorize` e `@EnableMethodSecurity`
- Autorizzazione **a livello di risorsa**: un utente può modificare solo il proprio profilo, un `ADMIN` qualsiasi profilo
- Messaggi di errore generici in fase di login per prevenire la *user enumeration*

---

## Gestione Errori

`@RestControllerAdvice` centralizzato, con formato di risposta coerente su tutta l'API:

| Eccezione | HTTP Status |
|---|---|
| `MethodArgumentNotValidException` | `400 Bad Request` |
| `AuthenticationException` | `401 Unauthorized` |
| `AccessDeniedException` | `403 Forbidden` |
| `ResourceNotFoundException` | `404 Not Found` |
| `ResourceConflictException` | `409 Conflict` |
| `Exception` (fallback) | `500 Internal Server Error` |

Il `401` da token JWT non valido è gestito direttamente nel `JwtAuthFilter`, poiché l'eccezione nasce prima del `DispatcherServlet` e non è intercettabile dal `@RestControllerAdvice`.

---

## Endpoint REST — 31 totali

### Auth (2)
| Metodo | Endpoint | Accesso |
|---|---|---|
| POST | `/api/auth/register` | Pubblico |
| POST | `/api/auth/login` | Pubblico |

### Utenti (6)
| Metodo | Endpoint | Accesso |
|---|---|---|
| GET | `/api/users/me` | Autenticato |
| GET | `/api/users` | **ADMIN** |
| GET | `/api/users/{id}` | Autenticato |
| PUT | `/api/users/{id}/role` | **ADMIN** |
| GET | `/api/users/{id}/profile` | Autenticato |
| PUT | `/api/users/{id}/profile` | Proprietario o **ADMIN** |

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
docker compose up --build
```

Un solo comando avvia MySQL 8 e l'applicazione. Al primo avvio MySQL esegue automaticamente `schema.sql` e `data.sql`, creando la struttura e popolando i dati essenziali. Il Dockerfile usa un multi-stage build (Maven → JRE), MySQL ha un healthcheck e l'app attende che sia pronto.

### Utenti precaricati

Tutti con password `password123`:

| Username | Ruolo |
|---|---|
| `manuel22` | ADMIN |
| `law_organizer` | ORGANIZER |
| `shanks_player` | USER |
| `zoro_player` | USER |

Il torneo di esempio è in stato `DRAFT` con 4 squadre iscritte: è possibile lanciare subito `generate-rounds` e vedere l'algoritmo di Berger all'opera.

---

## Avvio in locale

### Prerequisiti
Java 21, Maven 3.x, MySQL 8.x

Ogni proprietà in `application.properties` è sovrascrivibile da variabile d'ambiente e ha un default valido per lo sviluppo locale:

```properties
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/leaguemate_db?createDatabaseIfNotExist=true}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:root}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:root}
jwt.secret=${JWT_SECRET:<chiave-Base64>}
jwt.expiration=${JWT_EXPIRATION:86400000}
```

```bash
./mvnw spring-boot:run
```

---

## Testing

**111 test** con JUnit 5, Mockito, Spring Security Test e MockMvc — tutti verdi.
**Code coverage: 89%** (requisito minimo 35%).

### Test unitari (service, security, exception)

| Classe testata | Test | Descrizione |
|---|---|---|
| `TournamentServiceImpl` | 28 | CRUD, **algoritmo di Berger**, **classifica**, **statistiche**, co-organizzatori |
| `UserServiceImpl` | 18 | Registrazione, ruoli, **profilo con autorizzazione a livello di risorsa** |
| `TeamServiceImpl` | 9 | CRUD completo, unicità nome, vincoli di cancellazione |
| `TeamMemberServiceImpl` | 9 | Aggiunta membri, duplicati, rimozione |
| `GlobalExceptionHandler` | 7 | 400, 401, 404, 409, 500 e mascheramento messaggi |
| `MatchServiceImpl` | 5 | Aggiornamento risultato, caricamento eager |
| `JwtService` | 4 | Generazione, estrazione, validazione token |
| `JwtAuthFilter` | 4 | Token valido, mancante, malformato |
| `AuthServiceImpl` | 3 | Registrazione con hashing, login |
| `TournamentControllerSecurityTest` | 3 | **403 con USER, 201 con ORGANIZER** (`@WebMvcTest`) |

### Test di integrazione (H2 in memoria, `@SpringBootTest` + MockMvc)

| Classe | Test | Descrizione |
|---|---|---|
| `AuthIntegrationTest` | 10 | Flusso register→login→endpoint protetto, RBAC, 401 identici, password mai esposta |
| `TournamentFlowIntegrationTest` | 10 | Ciclo di vita completo del torneo end-to-end |
| `ApiApplicationTests` | 1 | Caricamento del contesto Spring |

I test di integrazione girano su un database H2 in memoria (profilo `test`), quindi l'intera suite si esegue senza un MySQL attivo.

### Coverage per package

| Package | Coverage |
|---|---|
| `security` | 100% |
| `exception` | 100% |
| `service.impl` | 96% |
| `controller` | 60% |
| **Totale** | **89%** |

> `dto` ed `entity` sono esclusi dal report (boilerplate Lombok). I controller sono **inclusi** e coperti dai test di integrazione.

```bash
./mvnw clean test
```
Report JaCoCo in `target/site/jacoco/index.html`.

---

## Deliverables

| Elemento | Stato |
|---|---|
| Codice sorgente completo | ✅ |
| Script SQL (`schema.sql` + `data.sql`) | ✅ |
| Collection Postman (39 richieste, 8 cartelle) | ✅ |
| Script Docker (`Dockerfile` + `docker-compose.yml`) | ✅ |
| Relazione tecnica | ✅ |

---

## Autore

**Manuel Barbagallo**  
ITS Prodigi — Full Stack Developer (2025–2027)  
[GitHub](https://github.com/Barbagallo2296) | [LinkedIn](https://linkedin.com/in/manuel-barbagallo/)