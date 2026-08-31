UPDATE hammerly.users
SET email = lower(btrim(email))
WHERE email <> lower(btrim(email));

CREATE UNIQUE INDEX users_normalized_email_unique
    ON hammerly.users ((lower(btrim(email))));
