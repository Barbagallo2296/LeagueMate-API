-- Girone all'italiana con andata e ritorno: se attivo, il calendario genera
-- anche le giornate di ritorno con casa/trasferta invertite.
ALTER TABLE tournaments ADD COLUMN double_round_robin BOOLEAN NOT NULL DEFAULT FALSE;
