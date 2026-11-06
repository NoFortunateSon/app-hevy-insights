# 0005 - Prima tabella: workout grezzi di Hevy e tipo di `user_id`

Stato: Accettata
Data: 2026-11-06

## Contesto
- L'ADR 0004 ha scelto Flyway e Testcontainers. Serve la prima migrazione (V1), quindi una prima scelta di modello dati.
- L'ADR 0003 prevede che `sync` salvi il JSON originale di Hevy e le righe normalizzate, e che ogni tabella abbia `user_id` dal primo giorno. Non esiste ancora una tabella utenti: multi-utente e autenticazione hanno un ADR proprio, in futuro.
- Hevy dichiara che l'API può cambiare: conservare il JSON originale permette di ricalcolare le tabelle normalizzate senza riscaricare.

Fatti verificati il 6/11/2026 (Maven Central, POM di Spring Boot 4.1.2; README di `actions/runner-images`):
- `spring-boot-starter-flyway` porta `flyway-core` ma **non** `flyway-database-postgresql`. Il BOM ne gestisce la versione (12.4.0): va dichiarato senza versione. Senza, Flyway 12 non supporta Postgres.
- `ubuntu-latest` corrisponde a Ubuntu 24.04, che ha Docker Server 28.0.4 installato. La prova vera è la CI verde di questa PR.
- Testcontainers 2.0.5 (BOM di Boot): la classe da usare è `org.testcontainers.postgresql.PostgreSQLContainer`; quella in `org.testcontainers.containers` è la vecchia.

## Opzioni

### Prima tabella
1. **`hevy_workout_raw`**: JSON originale di ogni workout (`payload JSONB`), con `hevy_updated_at` e `synced_at`; chiave `(user_id, hevy_workout_id)`.
   - Pro: è la prima cosa che scrive `sync`; non dipende dalla forma dell'API; le tabelle normalizzate si derivano dopo.
   - Contro: le cancellazioni lato Hevy vanno gestite a parte (prossime PR).
2. **`sync_state`**: solo il punto da cui ripartire con la sync.
   - Pro: piccolissima. Contro: nessun dato utile finché non arriva la tabella dei dati.
3. **`app_user`** con foreign key dalle tabelle future.
   - Pro: integrità referenziale da subito. Contro: anticipa il multi-utente che l'ADR 0003 rimanda.

### Tipo di `user_id`
1. **UUID**: opaco, generabile senza sequenza condivisa; per ora un valore fisso per l'unico utente.
2. **BIGINT**: compatto; con la tabella utenti diventerebbe una identity.
3. **TEXT con l'id utente Hevy**: lega il nostro modello a Hevy.

## Decisione
Deciso da Alessio il 6/11/2026:
- Prima tabella: **`hevy_workout_raw`** (opzione 1).
- `user_id`: **UUID**, senza foreign key finché non esiste una tabella utenti.

```sql
CREATE TABLE hevy_workout_raw (
    user_id          UUID        NOT NULL,
    hevy_workout_id  TEXT        NOT NULL,
    payload          JSONB       NOT NULL,
    hevy_updated_at  TIMESTAMPTZ NOT NULL,
    synced_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, hevy_workout_id)
);
```

## Conseguenze
- La V1 non si modifica più dopo il merge: ogni cambio è una nuova migrazione.
- Da decidere più avanti (non in questa PR): da dove arriva l'UUID dell'unico utente (configurazione o costante), come si registrano i workout cancellati su Hevy, quali tabelle normalizzate servono.
- I test di integrazione usano `postgres:18.6`, la stessa immagine del compose di `platform-infra`. Dependabot aggiorna il compose ma non la stringa nel codice Java: l'allineamento è manuale.
- `mvn verify` in locale richiede Docker Desktop acceso (anche il test di health ora avvia il contesto con il database).
