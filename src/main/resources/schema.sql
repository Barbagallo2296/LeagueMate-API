-- 1. Tabella Utenti
CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    role VARCHAR(20) NOT NULL
    );

-- 2. Tabella Profili Utente (Relazione 1:1 con users)
CREATE TABLE IF NOT EXISTS user_profiles (
                                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             bio TEXT,
                                             phone_number VARCHAR(20),
    avatar_url VARCHAR(255),
    user_id BIGINT NOT NULL UNIQUE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

-- 3. Tabella Squadre
CREATE TABLE IF NOT EXISTS teams (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     name VARCHAR(50) NOT NULL UNIQUE,
    logo_url VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- 4. Tabella Membri del Team (Corretta secondo l'Entity JPA)
CREATE TABLE IF NOT EXISTS team_members (
                                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                            user_id BIGINT NOT NULL,
                                            team_id BIGINT NOT NULL,
                                            team_role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
    );

-- 5. Tabella Tornei
CREATE TABLE IF NOT EXISTS tournaments (
                                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                           name VARCHAR(100) NOT NULL,
    season VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    points_for_win INT NOT NULL DEFAULT 3,
    points_for_draw INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- 6. Tabella Iscrizioni Torneo (Corretta con campo status)
CREATE TABLE IF NOT EXISTS tournament_registrations (
                                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                        registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                        status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    team_id BIGINT NOT NULL,
    tournament_id BIGINT NOT NULL,
    UNIQUE(team_id, tournament_id),
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
    );

-- 7. Tabella Giornate (Rounds)
CREATE TABLE IF NOT EXISTS rounds (
                                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                      round_number INT NOT NULL,
                                      tournament_id BIGINT NOT NULL,
                                      FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
    );

-- 8. Tabella Partite (Matches)
CREATE TABLE IF NOT EXISTS matches (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       home_score INT,
                                       away_score INT,
                                       status VARCHAR(20) NOT NULL,
    home_team_id BIGINT NOT NULL,
    away_team_id BIGINT NOT NULL,
    round_id BIGINT NOT NULL,
    FOREIGN KEY (home_team_id) REFERENCES teams(id),
    FOREIGN KEY (away_team_id) REFERENCES teams(id),
    FOREIGN KEY (round_id) REFERENCES rounds(id) ON DELETE CASCADE
    );