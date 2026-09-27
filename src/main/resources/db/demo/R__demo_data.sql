INSERT INTO users (id, username, email, password, first_name, last_name, role)
VALUES (1, 'manuel22', 'manuel@leaguemate.com',
        '$2a$10$ZQ0O6vOM2xUeypi/rxLoT.dqdHWXVMJO4DS6LPvZqEMdJ.T61OuOK',
        'Manuel', 'Barbagallo', 'ADMIN'),
       (2, 'law_organizer', 'law@leaguemate.com',
        '$2a$10$ZQ0O6vOM2xUeypi/rxLoT.dqdHWXVMJO4DS6LPvZqEMdJ.T61OuOK',
        'Trafalgar', 'Law', 'ORGANIZER'),
       (3, 'shanks_player', 'shanks@leaguemate.com',
        '$2a$10$ZQ0O6vOM2xUeypi/rxLoT.dqdHWXVMJO4DS6LPvZqEMdJ.T61OuOK',
        'Shanks', 'LeRoux', 'USER'),
       (4, 'zoro_player', 'zoro@leaguemate.com',
        '$2a$10$ZQ0O6vOM2xUeypi/rxLoT.dqdHWXVMJO4DS6LPvZqEMdJ.T61OuOK',
        'Roronoa', 'Zoro', 'USER')
    ON DUPLICATE KEY UPDATE id = id;

INSERT INTO user_profiles (id, bio, phone_number, avatar_url, user_id)
VALUES (1, 'Full Stack Developer e creatore di LeagueMate', '+39 333 1234567',
        'https://leaguemate.com/avatars/manuel.png', 1),
       (2, 'Organizzatore di tornei amatoriali', '+39 333 7654321',
        'https://leaguemate.com/avatars/law.png', 2),
       (3, 'Attaccante, capitano della Red Hair United', '+39 333 1112223',
        'https://leaguemate.com/avatars/shanks.png', 3),
       (4, 'Difensore centrale', '+39 333 4445556',
        'https://leaguemate.com/avatars/zoro.png', 4)
    ON DUPLICATE KEY UPDATE id = id;

INSERT INTO teams (id, name, logo_url)
VALUES (1, 'Straw Hat FC', 'https://leaguemate.com/logos/strawhat.png'),
       (2, 'Heart Pirates', 'https://leaguemate.com/logos/heart.png'),
       (3, 'Red Hair United', 'https://leaguemate.com/logos/redhair.png'),
       (4, 'Blackbeard City', 'https://leaguemate.com/logos/blackbeard.png')
    ON DUPLICATE KEY UPDATE id = id;

INSERT INTO team_members (id, user_id, team_id, team_role)
VALUES (1, 1, 1, 'CAPTAIN'),
       (2, 4, 1, 'PLAYER'),
       (3, 3, 3, 'CAPTAIN'),
       (4, 2, 2, 'CAPTAIN')
    ON DUPLICATE KEY UPDATE id = id;

INSERT INTO tournaments (id, name, season, status, points_for_win, points_for_draw)
VALUES (1, 'Grand Line Cup', '2026/2027', 'DRAFT', 3, 1)
    ON DUPLICATE KEY UPDATE id = id;

INSERT INTO tournament_registrations (id, team_id, tournament_id, status)
VALUES (1, 1, 1, 'CONFIRMED'),
       (2, 2, 1, 'CONFIRMED'),
       (3, 3, 1, 'CONFIRMED'),
       (4, 4, 1, 'CONFIRMED')
    ON DUPLICATE KEY UPDATE id = id;

INSERT INTO tournament_organizers (tournament_id, user_id)
VALUES (1, 2)
    ON DUPLICATE KEY UPDATE user_id = user_id;