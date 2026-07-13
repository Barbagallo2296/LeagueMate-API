-- Inserimento Utenti (Password in chiaro: password123)
INSERT INTO users (id, username, email, password, first_name, last_name, role)
VALUES (1, 'manuel22', 'manuel@leaguemate.com', '$2a$10$vI8aWBnd3X13K.A7/z.DduIskwU.YwXn1y6YVUzQoW3/w7yS2Ygde',
        'Manuel', 'Barbagallo', 'ADMIN'),
       (2, 'doc_friend', 'doc@leaguemate.com', '$2a$10$vI8aWBnd3X13K.A7/z.DduIskwU.YwXn1y6YVUzQoW3/w7yS2Ygde', 'Dottor',
        'Amico', 'USER') ON DUPLICATE KEY
UPDATE id=id;

-- Inserimento Profili Utente
INSERT INTO user_profiles (id, bio, phone_number, avatar_url, user_id)
VALUES (1, 'Full Stack Developer & LeagueMate Creator', '+393331234567', 'https://avatar.com/manuel.png', 1),
       (2, 'LeagueMate Premium User', '+393337654321', 'https://avatar.com/doc.png', 2) ON DUPLICATE KEY
UPDATE id=id;

-- Inserimento 4 Squadre (Perfette per il round-robin a 3 giornate)
INSERT INTO teams (id, name, logo_url)
VALUES (1, 'Straw Hat FC', 'https://images.com/luffy.png'),
       (2, 'Heart Pirates', 'https://images.com/law.png'),
       (3, 'Red Hair United', 'https://images.com/shanks.png'),
       (4, 'Blackbeard City', 'https://images.com/teach.png') ON DUPLICATE KEY
UPDATE id=id;

-- Inserimento Membri delle Squadre (Mappati su user_id e team_role corretti)
INSERT INTO team_members (id, user_id, team_id, team_role)
VALUES (1, 1, 1, 'CAPTAIN'),
       (2, 2, 1, 'PLAYER') ON DUPLICATE KEY
UPDATE id=id;

-- Inserimento Torneo in stato DRAFT
INSERT INTO tournaments (id, name, season, status, points_for_win, points_for_draw)
VALUES (1, 'Grand Line Cup', '2026/2027', 'DRAFT', 3, 1) ON DUPLICATE KEY
UPDATE id=id;

-- Iscrizione delle squadre con status CONFIRMED obbligatorio
INSERT INTO tournament_registrations (id, team_id, tournament_id, status)
VALUES (1, 1, 1, 'CONFIRMED'),
       (2, 2, 1, 'CONFIRMED'),
       (3, 3, 1, 'CONFIRMED'),
       (4, 4, 1, 'CONFIRMED') ON DUPLICATE KEY
UPDATE id=id;