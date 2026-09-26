-- La bio era TEXT, ma la validazione applicativa la limita a 500 caratteri
-- (UpdateUserProfileRequest): VARCHAR(500) allinea database, entity e DTO.
ALTER TABLE user_profiles MODIFY bio VARCHAR(500);
