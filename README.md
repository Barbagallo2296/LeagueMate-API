# LeagueMate API

[![CI](https://github.com/Barbagallo2296/LeagueMate-API/actions/workflows/ci.yml/badge.svg)](https://github.com/Barbagallo2296/LeagueMate-API/actions/workflows/ci.yml)

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
| Spring Authorization Server | 7.1.x (parte di Spring Security) |
| Spring OAuth2 Resource Server | 7.1.x |
| Flyway | 12.x |
| Spring Boot Actuator | 4.1.x |
| springdoc-openapi (Swagger UI) | 3.1.x |
| Bucket4j | 8.x |
| Testcontainers | 2.x |
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
├── controller/ # Endpoint REST
├── service/ # Interfacce di business logic
│ └── impl/ # Implementazioni
├── repository/ # Interfacce Spring Data JPA
├── entity/ # Entity JPA su MySQL
├── config/ # Configurazione OpenAPI
├── dto/ # Java Records (input/output)
├── mapper/ # Conversione Entity → DTO, in un unico punto
├── security/ # Authorization Server, token opachi, SecurityConfig, ownership dei tornei
└── exception/ # Handler eccezioni

src/main/resources/db/
├── migration/ # Schema versionato Flyway (V1, V2, V3...)
└── demo/ # Dati di esempio (migrazione ripetibile, solo su richiesta)
```

I mapper sono classi statiche senza stato. I service restituiscono un DTO quando la conversione legge associazioni LAZY (es. `TeamMemberService`, `UserService.getProfile`), perché con `open-in-view=false` deve avvenire dentro la transazione; negli altri casi restituiscono l'entity e la conversione avviene nel controller.


### Moduli funzionali

| Modulo | Controller | Service | Descrizione |
|---|---|---|---|
| **Auth** | `AuthController` | `AuthService` | Registrazione, login, rinnovo e revoca dei token |
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
| `@ManyToMany`  | `User` ↔ `Team` tramite `TeamMember` | Attributi: `teamRole`, `joinedAt` |
| `@ManyToMany`  | `Tournament` ↔ `Team` tramite `TournamentRegistration` | Attributi: `status`, `registeredAt` |

**Scelta progettuale:** dove la relazione N:N porta attributi propri si usa un'entità di giunzione (modellazione corretta); dove non ne porta si usa `@ManyToMany` pura con `@JoinTable`. Nel progetto c'è una sola `@ManyToMany` pura (torneo-organizzatori); le altre due N:N sono modellate con entità di giunzione.

### Elementi avanzati JPA

| Elemento | Dove | Perché |
|---|---|---|
| `FetchType.LAZY` | Tutte le relazioni `@ManyToOne` e `@ManyToMany` | Evita query non necessarie |
| **JPQL con `@Query` + `@Param`** | `MatchRepository`, `TournamentRepository`, `TournamentRegistrationRepository`, `TeamMemberRepository` | Query esplicite e type-safe |
| **`JOIN FETCH`** | `findCompletedMatchesWithTeams()`, `findByRoundIdWithTeams()`, `findConfirmedWithTeams()`, `findByIdWithTeams()`, `findByTeamIdWithUserAndTeam()` | **Risolve problemi N+1 reali** nel calcolo della classifica e nell'elenco dei membri |
| **Query di aggregazione (`COUNT`, `GROUP BY`)** | `countByStatus()` (proiezione a interfaccia), `countMatchesByTournamentAndStatus()`, `countByRole()` | Statistiche e regole di dominio |
| **Query derivate dal nome** | `existsByTeamIdAndUserId()`, `existsByUsername()`, `countByRole()` | Controlli senza caricare intere tabelle |
| **`@EntityGraph`** | `findWithRegistrationsById()` | Fetch dichiarativo, usato nell'iscrizione delle squadre |

### Schema del database (Flyway)

Lo schema è gestito **solo da Flyway**: ogni modifica è una migrazione versionata in `db/migration` (`V1__init_schema.sql`, `V2__limit_profile_bio_length.sql`, `V3__add_double_round_robin.sql`). Hibernate gira con `ddl-auto=validate`: non modifica mai il database e all'avvio verifica che le entity corrispondano alle tabelle.

- I **test di integrazione** applicano le stesse migrazioni su H2, quindi girano sullo schema reale.
- I **dati demo** (`db/demo/R__demo_data.sql`) si caricano solo aggiungendo `classpath:db/demo` a `FLYWAY_LOCATIONS`, come fa il `docker-compose.yml`.
- Un database creato prima di Flyway viene registrato come versione 1 (`baseline-on-migrate`) e riceve solo le migrazioni successive.

### Configurazione JPA

`spring.jpa.open-in-view=false` — disattivato di proposito. La sessione Hibernate non resta aperta durante la serializzazione della risposta: ogni endpoint carica esplicitamente le associazioni necessarie tramite `JOIN FETCH` o `@EntityGraph`, evitando accessi lazy nascosti fuori dalla transazione.

### Entity

- **User** — implementa `UserDetails`, ruoli enum (`ADMIN`, `ORGANIZER`, `USER`)
- **UserProfile** — dati aggiuntivi (bio, avatar, telefono), relazione `@OneToOne`
- **Team** — squadra con membri e iscrizioni
- **TeamMember** — giunzione ricca User↔Team con `TeamRole` (CAPTAIN/PLAYER/RESERVE) e `joinedAt`
- **Tournament** — torneo con stagione, stato, configurazione punti, formula (sola andata o andata e ritorno) e **co-organizzatori**
- **TournamentRegistration** — giunzione ricca Tournament↔Team con `RegistrationStatus` e `registeredAt`
- **Round** — giornata del torneo
- **Match** — partita con squadra casa/trasferta, score e stato

---

## Funzionalità principali

### Generazione calendario (round-robin, metodo del cerchio)
`generateRounds()` genera il calendario all'italiana con il **metodo del cerchio** (circle method), che produce lo stesso calendario delle tabelle di Berger: una squadra resta fissa e le altre ruotano attorno a essa. Con N squadre genera N-1 giornate da N/2 partite, e ogni coppia si incontra esattamente una volta. Gestisce il numero dispari con un turno di riposo e alterna casa/trasferta fra le giornate. Se il torneo è creato con `"doubleRoundRobin": true`, dopo l'andata genera il **girone di ritorno**: stesse giornate nello stesso ordine, con casa e trasferta invertite (N squadre → 2(N-1) giornate). La generazione è consentita **solo in stato `DRAFT`**, per non cancellare risultati già registrati.

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
- I risultati si inseriscono solo in un torneo `ACTIVE`
- Un torneo si chiude (`ACTIVE → COMPLETED`) solo quando tutte le partite sono state giocate; da quel momento i risultati sono bloccati
- Non è possibile declassare l'ultimo `ADMIN` rimasto
- I punti per vittoria e pareggio si modificano solo in stato `DRAFT`: dopo l'avvio si possono cambiare solo nome e stagione
- Solo un utente con ruolo `ORGANIZER` o `ADMIN` può diventare co-organizzatore di un torneo

---

## Sicurezza

- Autenticazione con **Spring Authorization Server**: al login vengono emessi un **access token opaco** (una stringa casuale senza dati, valida 15 minuti) e un **refresh token** (valido 7 giorni). Le durate sono configurabili con `ACCESS_TOKEN_TTL` e `REFRESH_TOKEN_TTL`
- **Rotazione dei refresh token**: ogni rinnovo emette una nuova coppia e invalida quella precedente (`reuseRefreshTokens=false` nelle `TokenSettings` del client registrato)
- **Token salvati solo come hash SHA-512**, come in Django Knox: `HashedOAuth2AuthorizationService` implementa l'`OAuth2AuthorizationService` dell'Authorization Server e nel database (tabella `oauth2_authorizations`) non scrive mai il token in chiaro
- **Revoca immediata**: `logout` elimina la sessione corrente, `logout-all` tutte le sessioni dell'utente su ogni dispositivo
- Il backend è anche **Resource Server**: ogni richiesta con `Authorization: Bearer <token>` è verificata da `StoredTokenIntrospector`, che legge l'autorizzazione direttamente dal database (nessuna chiamata HTTP) e controlla scadenza ed esistenza dell'utente
- Le autorizzazioni scadute vengono eliminate ogni ora da un task programmato (`ExpiredTokenCleanup`)
- Password hashate con **BCrypt**
- Autorizzazione **per ruolo** (RBAC) tramite `@PreAuthorize` e `@EnableMethodSecurity`
- Autorizzazione **a livello di risorsa**: un utente può modificare solo il proprio profilo, un `ADMIN` qualsiasi profilo
- **Ownership dei tornei**: chi crea un torneo ne diventa organizzatore; un `ORGANIZER` può gestire (modifica, iscrizioni, calendario, risultati, chiusura, co-organizzatori) **solo i tornei di cui è organizzatore**, tramite il bean `TournamentSecurity` usato nelle espressioni `@PreAuthorize`. Un `ADMIN` può gestire qualsiasi torneo
- Richiesta senza token → `401`; autenticato ma senza permessi → `403` (`AuthenticationEntryPoint` e `AccessDeniedHandler` dedicati)
- L'email di un utente è visibile solo all'utente stesso o a un `ADMIN`
- Messaggi di errore generici in fase di login per prevenire la *user enumeration*
- **Rate limit sul login** (`LoginRateLimitFilter`, Bucket4j): 10 tentativi al minuto per IP, oltre i quali la risposta è `429 Too Many Requests` con header `Retry-After`. Configurabile con `LOGIN_RATE_LIMIT_CAPACITY` e `LOGIN_RATE_LIMIT_PERIOD`
- **CORS** abilitato per il frontend: le origini ammesse si configurano con `CORS_ALLOWED_ORIGINS` (separate da virgola; default `http://localhost:5173,http://localhost:3000`, le porte di sviluppo di Vite e Create React App)

---

## Gestione Errori

`@RestControllerAdvice` centralizzato, con formato di risposta coerente su tutta l'API:

| Eccezione | HTTP Status |
|---|---|
| `MethodArgumentNotValidException` | `400 Bad Request` |
| `HttpMessageNotReadableException` (JSON malformato) | `400 Bad Request` |
| `MethodArgumentTypeMismatchException` (path variable non valido) | `400 Bad Request` |
| `AuthenticationException` | `401 Unauthorized` |
| `AccessDeniedException` | `403 Forbidden` |
| `ResourceNotFoundException`, `NoResourceFoundException` | `404 Not Found` |
| `HttpRequestMethodNotSupportedException` | `405 Method Not Allowed` |
| `ResourceConflictException`, `DataIntegrityViolationException` | `409 Conflict` |
| `HttpMediaTypeNotSupportedException` | `415 Unsupported Media Type` |
| `Exception` (fallback, con stack trace nei log) | `500 Internal Server Error` |

Gli errori che nascono nella filter chain di Spring Security (token non valido, token mancante, accesso negato a livello URL) avvengono prima del `DispatcherServlet` e non sono intercettabili dal `@RestControllerAdvice`: li scrive `SecurityErrorResponse`, con lo stesso formato JSON.

---

## Endpoint REST — 38 totali

Le liste di tornei, squadre e utenti sono **paginate**: `?page=0&size=20&sort=name,asc` (default 20 elementi, massimo 100). La risposta ha la forma `{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }`. Il parametro `sort` accetta solo i campi ammessi da ciascun endpoint; un campo diverso restituisce `400`.

### Auth (5)
| Metodo | Endpoint | Accesso |
|---|---|---|
| POST | `/api/auth/register` | Pubblico |
| POST | `/api/auth/login` | Pubblico — restituisce `access_token`, `refresh_token`, `token_type`, `expires_in` |
| POST | `/api/auth/refresh` | Pubblico — body `{"refresh_token": "..."}`, restituisce una nuova coppia |
| POST | `/api/auth/logout` | Autenticato — revoca la sessione corrente |
| POST | `/api/auth/logout-all` | Autenticato — revoca tutte le sessioni dell'utente |

### Utenti (6)
| Metodo | Endpoint | Accesso |
|---|---|---|
| GET | `/api/users/me` | Autenticato |
| GET | `/api/users` | **ADMIN** |
| GET | `/api/users/{id}` | Autenticato |
| PUT | `/api/users/{id}/role` | **ADMIN** |
| GET | `/api/users/{id}/profile` | Autenticato |
| PUT | `/api/users/{id}/profile` | Proprietario o **ADMIN** |

### Tornei (14)
| Metodo | Endpoint | Accesso |
|---|---|---|
| POST | `/api/tournaments` | **ADMIN / ORGANIZER** |
| GET | `/api/tournaments` | Autenticato |
| GET | `/api/tournaments/mine` | Autenticato — tornei di cui l'utente è organizzatore |
| GET | `/api/tournaments/{id}` | Autenticato |
| GET | `/api/tournaments/status/{status}` | Autenticato |
| PUT | `/api/tournaments/{id}` | **ADMIN / organizzatore del torneo** |
| DELETE | `/api/tournaments/{id}` | **ADMIN** |
| POST | `/api/tournaments/{id}/register-team/{teamId}` | **ADMIN / organizzatore del torneo** |
| POST | `/api/tournaments/{id}/generate-rounds` | **ADMIN / organizzatore del torneo** |
| POST | `/api/tournaments/{id}/complete` | **ADMIN / organizzatore del torneo** |
| GET | `/api/tournaments/{id}/standings` | Autenticato |
| GET | `/api/tournaments/{id}/stats` | Autenticato |
| GET | `/api/tournaments/{id}/rounds` | Autenticato — calendario completo: giornate con le partite |
| GET | `/api/tournaments/{id}/teams` | Autenticato — squadre iscritte |

### Co-organizzatori — `@ManyToMany` (3)
| Metodo | Endpoint | Accesso |
|---|---|---|
| POST | `/api/tournaments/{id}/organizers/{userId}` | **ADMIN / organizzatore del torneo** |
| GET | `/api/tournaments/{id}/organizers` | Autenticato |
| DELETE | `/api/tournaments/{id}/organizers/{userId}` | **ADMIN / organizzatore del torneo** |

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
| PUT | `/api/matches/{id}/result` | **ADMIN / organizzatore del torneo** |
| GET | `/api/matches/round/{roundId}` | Autenticato |

---

## Documentazione API e health check

- **Swagger UI**: `http://localhost:8080/swagger-ui.html` — documentazione interattiva di tutti gli endpoint. Con il pulsante *Authorize* si incolla l'`access_token` ottenuto dal login. Disattivabile con `SWAGGER_ENABLED=false`.
- **Specifica OpenAPI**: `http://localhost:8080/v3/api-docs`
- **Health check**: `http://localhost:8080/actuator/health` — pubblico, restituisce solo `{"status":"UP"}`. Nessun altro endpoint di Actuator è esposto.

---

## Avvio con Docker (consigliato)

```bash
docker compose up --build
```

Nessuna configurazione è obbligatoria: ogni variabile ha un valore predefinito. Per personalizzarle (password MySQL, durata dei token, porte) si copia `.env.example` in `.env`:

```bash
cp .env.example .env
```

Un solo comando avvia MySQL 8 e l'applicazione. All'avvio Flyway applica le migrazioni dello schema e carica i dati demo. Il Dockerfile scarica le dipendenze Maven in un layer separato (le build successive riusano la cache se il `pom.xml` non cambia), l'applicazione gira con un utente non-root e il container ha un `HEALTHCHECK` sull'endpoint di Actuator. Il build è multi-stage (Maven → JRE), MySQL ha un healthcheck e l'app attende che sia pronto.

### Utenti precaricati

Tutti con password `password123`:

| Username | Ruolo |
|---|---|
| `manuel22` | ADMIN |
| `law_organizer` | ORGANIZER |
| `shanks_player` | USER |
| `zoro_player` | USER |

Il torneo di esempio è in stato `DRAFT` con 4 squadre iscritte e `law_organizer` come organizzatore: è possibile lanciare subito `generate-rounds` e vedere il calendario generato dal metodo del cerchio.

---

## Avvio in locale

### Prerequisiti
Java 21, Maven 3.x, MySQL 8.x

Ogni proprietà in `application.properties` è sovrascrivibile da variabile d'ambiente e ha un valore predefinito per lo sviluppo locale:

```properties
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/leaguemate_db?createDatabaseIfNotExist=true}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:root}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:root}
app.auth.access-token-ttl=${ACCESS_TOKEN_TTL:15m}
app.auth.refresh-token-ttl=${REFRESH_TOKEN_TTL:7d}
```

```bash
export FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/demo   # facoltativo: dati demo
./mvnw spring-boot:run
```

---

## Testing

**157 test** con JUnit 5, Mockito, Spring Security Test e MockMvc — tutti verdi.
**Code coverage: 92%** (requisito minimo 35%).

### Test unitari (service, security, exception)

| Classe testata | Test | Descrizione |
|---|---|---|
| `TournamentServiceImpl` | 41 | CRUD, **generazione calendario** (anche andata e ritorno), **classifica**, **statistiche**, co-organizzatori, chiusura torneo, calendario e squadre iscritte |
| `UserServiceImpl` | 18 | Registrazione, ruoli, **profilo con autorizzazione a livello di risorsa** |
| `TeamServiceImpl` | 9 | CRUD completo, unicità nome, vincoli di cancellazione |
| `TeamMemberServiceImpl` | 10 | Aggiunta membri, duplicati, rimozione vincolata alla squadra |
| `GlobalExceptionHandler` | 8 | 400, 401, 404, 409, 500, token non valido e mascheramento messaggi |
| `MatchServiceImpl` | 7 | Aggiornamento risultato, blocco su torneo non attivo, giornata inesistente, caricamento eager |
| `AuthServiceImpl` | 6 | Registrazione con hashing, login, rinnovo, logout e logout-all |
| `TournamentControllerSecurityTest` | 3 | **403 con USER, 201 con ORGANIZER** (`@WebMvcTest`) |

### Test di integrazione (H2 in memoria, `@SpringBootTest` + MockMvc)

| Classe | Test | Descrizione |
|---|---|---|
| `AuthIntegrationTest` | 10 | Flusso register→login→endpoint protetto, RBAC, 401 identici, password mai esposta |
| `TokenAuthenticationIntegrationTest` | 10 | Token opachi, hash nel database, rotazione dei refresh token, logout, logout-all, scadenze, pulizia automatica |
| `TournamentFlowIntegrationTest` | 10 | Ciclo di vita completo del torneo end-to-end |
| `InfrastructureIntegrationTest` | 6 | Rate limit sul login, health check, specifica OpenAPI |
| `MySqlSchemaIntegrationTest` | 1 | Migrazioni Flyway e dati demo su **MySQL 8 reale** (Testcontainers) |
| `AuthorizationRulesIntegrationTest` | 17 | Ownership dei tornei, chiusura torneo, andata e ritorno, paginazione, calendario, squadre iscritte, i miei tornei, CORS, membri, privacy email, 401/400/404/409 |
| `ApiApplicationTests` | 1 | Caricamento del contesto Spring |

I test di integrazione girano su un database H2 in memoria (profilo `test`) su cui Flyway applica le stesse migrazioni della produzione, quindi la suite si esegue senza un MySQL attivo. `MySqlSchemaIntegrationTest` avvia invece un MySQL 8 in un container: gira quando Docker è disponibile (sempre in CI) e viene saltato altrimenti.

### Coverage per package

| Package | Coverage |
|---|---|
| `security` | 92% |
| `exception` | 83% |
| `service.impl` | 97% |
| `mapper` | 100% |
| `config` | 100% |
| `controller` | 79% |
| **Totale** | **92%** |

> `dto` ed `entity` sono esclusi dal report (boilerplate Lombok). I controller sono **inclusi** e coperti dai test di integrazione.

```bash
./mvnw clean test
```
Report JaCoCo in `target/site/jacoco/index.html`.

### Integrazione continua

A ogni push su `main` e a ogni pull request GitHub Actions (`.github/workflows/ci.yml`) esegue `mvnw verify` con JDK 21, incluso il test su MySQL reale, e pubblica il report JaCoCo come artifact.

---

## Deliverables

| Elemento | Stato |
|---|---|
| Codice sorgente completo | ✅ |
| Script SQL (migrazioni Flyway in `db/migration` + dati demo in `db/demo`) | ✅ |
| Collection Postman (39 richieste, 8 cartelle) | ✅ |
| Script Docker (`Dockerfile` + `docker-compose.yml`) | ✅ |
| Relazione tecnica | ✅ |

---

## Autore

**Manuel Barbagallo**  
ITS Prodigi — Full Stack Developer (2025–2027)  
[GitHub](https://github.com/Barbagallo2296) | [LinkedIn](https://linkedin.com/in/manuel-barbagallo/)