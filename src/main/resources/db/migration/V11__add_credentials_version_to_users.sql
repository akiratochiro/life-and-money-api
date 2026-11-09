-- Sobe a cada troca de senha. O access token leva a versão em que foi emitido, e um token de uma
-- versão anterior é recusado: trocar a senha encerra também os access tokens já emitidos, e não só
-- os refresh tokens. Começa em 0, o mesmo valor que um token sem a versão (emitido antes desta
-- migration) assume, para o deploy não derrubar quem está logado.
ALTER TABLE users ADD COLUMN credentials_version INTEGER NOT NULL DEFAULT 0;
