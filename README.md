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
Il metodo `generateRounds()` in `TournamentServiceImpl` implementa l'algoritmo Round Robin di Berger per generare automaticamente le giornate di un girone all'italiana. Gestisce il numero dispari di squadre con un turno di riposo e alterna casa/trasferta ad ogni giornata. Con N squadre genera N-1 giornate da N/2 partite ciascuna.

### Calcolo classifica dinamico (Stream API)
Il metodo `calculateStandings()` calcola la classifica in tempo reale leggendo le partite con stato `COMPLETED` tramite Stream API. La classifica non è persistita sul database, evitando rischi di disallineamento.

```java
return statsMap.entrySet().stream()
    .map(entry -> new StandingEntry(...))
    .sorted(Comparator.comparingInt(StandingEntry::points).reversed()
        .thenComparing(Comparator.comparingInt(StandingEntry::goalDifference).reversed()))
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
- Password hashate con **BCrypt**
- Autorizzazione basata su ruoli tramite `@PreAuthorize` e `@EnableMethodSecurity`

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
| POST | `/api/tournaments` | **ADMIN / ORGANIZER** | Crea un nuovo torneo |
| GET | `/api/tournaments` | Autenticato | Lista tutti i tornei |
| GET | `/api/tournaments/{id}` | Autenticato | Dettaglio torneo |
| GET | `/api/tournaments/status/{status}` | Autenticato | Filtra per stato |
| POST | `/api/tournaments/{id}/register-team/{teamId}` | **ADMIN / ORGANIZER** | Iscrive una squadra |
| POST | `/api/tournaments/{id}/generate-rounds` | **ADMIN / ORGANIZER** | Genera il calendario |
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
| PUT | `/api/matches/{id}/result` | **ADMIN / ORGANIZER** | Inserisce il risultato |
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

Avvia automaticamente MySQL 8 e l'applicazione Spring Boot con un solo comando. Il Dockerfile usa un multi-stage build (Maven per la compilazione, JRE Alpine per l'esecuzione).

---

## Testing

**29 test unitari** con JUnit 5 e Mockito — tutti verdi ✅
**Code coverage: 75%** (misurata con JaCoCo, requisito minimo 35%)

| Classe testata | Test | Descrizione |
|---|---|---|
| `UserServiceImpl` | 5 | Registrazione, duplicati email/username, findByUsername |
| `TournamentServiceImpl` | 7 | CRUD, **algoritmo di Berger**, **calcolo classifica** |
| `TeamServiceImpl` | 4 | Creazione, unicità nome, recupero |
| `MatchServiceImpl` | 2 | Aggiornamento risultato, not found |
| `AuthServiceImpl` | 3 | Registrazione con hashing, login, generazione token |
| `JwtService` | 4 | Generazione token, estrazione username, validazione |
| `GlobalExceptionHandler` | 4 | 404, 409, 400, 500 |

### Coverage per package

| Package | Coverage |
|---|---|
| `exception` | 100% |
| `service.impl` | 78% |
| `security` | 45% |
| **Totale** | **75%** |

### Esecuzione test
```bash
mvn clean test
```
Il report JaCoCo viene generato in `target/site/jacoco/index.html`.

---

## Deliverables

| Elemento | Stato |
|---|---|
| Codice sorgente completo | ✅ |
| Script SQL (`schema.sql` + `data.sql`) | ✅ |
| Collection Postman | ✅ |
| Script Docker (`Dockerfile` + `docker-compose.yml`) | ✅ |
| Relazione tecnica | ✅ |

---

## Stato del progetto

| Layer | Stato |
|---|---|
| Entity | ✅ Completo |
| Repository | ✅ Completo |
| Service | ✅ Completo |
| Security (JWT + RBAC) | ✅ Completo |
| Controller | ✅ Completo |
| Docker | ✅ Completo |
| Test (JUnit 5 + Mockito) | ✅ 29/29 verdi — coverage 75% |
| Script SQL | ✅ Completo |
| Postman Collection | ✅ Completo |
| Relazione tecnica | ✅ Completo |

---

## Autore

**Manuel Barbagallo**  
ITS Prodigi — Full Stack Developer (2025–2027)  
[GitHub](https://github.com/Barbagallo2296) | [LinkedIn](https://linkedin.com/in/manuel-barbagallo/)