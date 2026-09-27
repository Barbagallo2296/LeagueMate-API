CREATE TABLE oauth2_authorizations
(
    id                       VARCHAR(100) PRIMARY KEY,
    registered_client_id     VARCHAR(100) NOT NULL,
    principal_name           VARCHAR(50)  NOT NULL,
    authorization_grant_type VARCHAR(100) NOT NULL,
    authorized_scopes        VARCHAR(500),
    access_token_hash        VARCHAR(128) NOT NULL,
    access_token_issued_at   DATETIME(6)  NOT NULL,
    access_token_expires_at  DATETIME(6)  NOT NULL,
    refresh_token_hash       VARCHAR(128),
    refresh_token_issued_at  DATETIME(6),
    refresh_token_expires_at DATETIME(6),
    CONSTRAINT uk_oauth2_access_token UNIQUE (access_token_hash),
    CONSTRAINT uk_oauth2_refresh_token UNIQUE (refresh_token_hash)
);

CREATE INDEX idx_oauth2_authorizations_principal ON oauth2_authorizations (principal_name);
