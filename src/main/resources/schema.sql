-- 1. Utenti
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    email      VARCHAR(100) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    first_name VARCHAR(50)  NOT NULL,
    last_name  VARCHAR(50)  NOT NULL,
    role       VARCHAR(20)  NOT NULL
    );

-- ---------------------------------------------------------------------
-- 2. Profili utente
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_profiles
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    bio          TEXT,
    phone_number VARCHAR(20),
    avatar_url   VARCHAR(255),
    user_id      BIGINT NOT NULL UNIQUE,
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
    );

-- ---------------------------------------------------------------------
-- 3. Squadre
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS teams
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(50) NOT NULL UNIQUE,
    logo_url   VARCHAR(255),
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- ---------------------------------------------------------------------
-- 4. Membri delle squadre
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS team_members
(
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id   BIGINT      NOT NULL,
    team_id   BIGINT      NOT NULL,
    team_role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_member_user_team UNIQUE (user_id, team_id),
    CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE
    );

-- ---------------------------------------------------------------------
-- 5. Tornei
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tournaments
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    season          VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    points_for_win  INT          NOT NULL DEFAULT 3,
    points_for_draw INT          NOT NULL DEFAULT 1,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- ---------------------------------------------------------------------
-- 6. Iscrizioni ai tornei
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tournament_registrations
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    registered_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status        VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    team_id       BIGINT      NOT NULL,
    tournament_id BIGINT      NOT NULL,
    CONSTRAINT uk_registration_team_tournament UNIQUE (team_id, tournament_id),
    CONSTRAINT fk_registration_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_registration_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id) ON DELETE CASCADE
    );

-- ---------------------------------------------------------------------
-- 7. Giornate
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rounds
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    round_number  INT    NOT NULL,
    tournament_id BIGINT NOT NULL,
    CONSTRAINT fk_round_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id) ON DELETE CASCADE
    );

-- ---------------------------------------------------------------------
-- 8. Partite
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS matches
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    home_score   INT,
    away_score   INT,
    status       VARCHAR(20) NOT NULL,
    home_team_id BIGINT      NOT NULL,
    away_team_id BIGINT      NOT NULL,
    round_id     BIGINT      NOT NULL,
    CONSTRAINT fk_match_home_team FOREIGN KEY (home_team_id) REFERENCES teams (id),
    CONSTRAINT fk_match_away_team FOREIGN KEY (away_team_id) REFERENCES teams (id),
    CONSTRAINT fk_match_round FOREIGN KEY (round_id) REFERENCES rounds (id) ON DELETE CASCADE
    );

-- ---------------------------------------------------------------------
-- 9. Co-organizzatori
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tournament_organizers
(
    tournament_id BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    PRIMARY KEY (tournament_id, user_id),
    CONSTRAINT fk_organizer_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id) ON DELETE CASCADE,
    CONSTRAINT fk_organizer_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
    );