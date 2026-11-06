# 0004 - Migrazioni del database e test di integrazione con Postgres

Stato: Accettata
Data: 2026-11-06

## Contesto
- L'ADR 0003 prevede Postgres, un `user_id` su ogni tabella, il JSON originale di Hevy e le righe normalizzate. Prima di scrivere la prima tabella servono due scelte che costa cambiare dopo:
  1. **come si crea e si evolve lo schema** (strumento di migrazione);
  2. **contro quale Postgres girano i test di integrazione**, in locale e in CI.
- Il CLAUDE.md del repo chiede di decidere il punto 2 in un ADR.
- Vincoli reali:
  - PC di Alessio: 15,7 GB di RAM, Windows 11.
  - La CLI di Docker è installata, ma il 6/11/2026 il motore non era avviato (errore di connessione a `dockerDesktopLinuxEngine`). Ogni test che usa container richiede Docker Desktop acceso.
  - `platform-infra` oggi contiene solo il README: il compose condiviso non esiste ancora.
  - CI su GitHub Actions, runner `ubuntu-latest`, piano Free.

Fatti verificati il 6/11/2026 (Maven Central, BOM di `spring-boot-dependencies` 4.1.2):

| Libreria | Versione gestita da Boot 4.1.2 | Ultima su Maven Central |
|---|---|---|
| Flyway (`flyway-core`) | 12.4.0 | 13.12.1 |
| Liquibase (`liquibase-core`) | 5.0.4 | 5.0.4 |
| Testcontainers (`testcontainers-postgresql`, linea 2.x) | 2.0.5 | 2.0.5 |
| Driver PostgreSQL | 42.7.14 | - |

- Boot 4.1.2 ha starter dedicati: `spring-boot-starter-flyway`, `spring-boot-starter-liquibase` (con le varianti `-test`) e `spring-boot-testcontainers`.
- Liquibase dalla 5.0 è rilasciato con la licenza **FSL-1.1-ALv2** (diventa Apache 2.0 dopo due anni). Liquibase stessa dichiara che la FSL non soddisfa la definizione di open source dell'OSI e che l'uso quotidiano non cambia. Fonte: [blog Liquibase](https://www.liquibase.com/blog/liquibase-community-for-the-future-fsl), [note di rilascio 5.0](https://docs.liquibase.com/community/release-notes/5-0).
- Flyway Community è sotto licenza Apache 2.0.
- **Ipotesi da verificare nella PR di implementazione:**
  - da Flyway 10 il supporto a Postgres sta in un modulo separato (`flyway-database-postgresql`); non ho controllato se lo starter di Boot lo include;
  - il runner `ubuntu-latest` ha Docker pronto per Testcontainers senza configurazione (lo dice un [post di Docker del 2023](https://www.docker.com/blog/running-testcontainers-tests-using-github-actions/), non la documentazione attuale dei runner).

## Opzioni

### Parte 1 - Migrazioni dello schema
1. **Flyway** (script SQL numerati, `V1__init.sql`, ...)
   - Pro: SQL puro, quindi lo schema si legge così com'è; licenza Apache 2.0; meno concetti da imparare.
   - Contro: rollback automatici solo nelle edizioni a pagamento (nella Community si scrive una nuova migrazione che annulla); Boot gestisce la 12.4.0 mentre su Central c'è già la 13.x.
2. **Liquibase** (changelog in YAML/XML/SQL)
   - Pro: changelog indipendenti dal database; rollback dichiarativi anche nella Community; diff tra schemi.
   - Contro: più concetti (changeset, contesti, tabella di lock); licenza FSL dalla 5.0, che non è open source per l'OSI; per un solo database (Postgres) l'indipendenza dal DB non serve.
3. **Nessuno strumento: Hibernate `ddl-auto`**
   - Pro: zero file da scrivere all'inizio.
   - Contro: nessuna storia delle modifiche; rischio di perdere dati quando cambia lo schema; non adatto alla tabella con il JSON originale, che deve restare stabile. Lo cito solo per completezza.

### Parte 2 - Postgres per i test di integrazione
1. **Testcontainers** (un Postgres in container avviato dai test, collegato con `@ServiceConnection`)
   - Pro: ogni test ha un DB pulito alla versione scelta; uguale in locale e in CI; nessuna dipendenza da `platform-infra`.
   - Contro: serve Docker acceso anche per un semplice `mvn verify` in locale; avvio più lento (qualche secondo per ogni contesto, riducibile riusando il container); qualche centinaio di MB di RAM durante i test.
2. **Compose condiviso di `platform-infra`** (i test usano il Postgres già acceso, con un DB e un utente per progetto)
   - Pro: un solo Postgres per tutti i progetti; test più veloci quando è già acceso.
   - Contro: il compose oggi non esiste; i test dipendono da un altro repo e dal suo stato (dati residui, versione); in CI servirebbe un `services: postgres` separato, quindi ambienti diversi tra locale e CI.
3. **Ibrido**: Testcontainers per i test, compose condiviso solo per far girare l'app in locale
   - Pro: test isolati e riproducibili; l'app in locale usa il Postgres comune, come prevede lo stack.
   - Contro: due configurazioni da tenere allineate (versione di Postgres soprattutto).
4. **Non fare test di integrazione per ora** (solo test unitari sulle metriche)
   - Pro: zero costo subito.
   - Contro: query, migrazioni e mappature JSON non verrebbero mai provate su un Postgres vero; contraddice il CLAUDE.md.

## Decisione
Deciso da Alessio il 6/11/2026:
- **Migrazioni: Flyway** (parte 1, opzione 1), alla versione gestita dal BOM di Spring Boot.
- **Test di integrazione: ibrido** (parte 2, opzione 3). I test usano Testcontainers; il compose condiviso di `platform-infra` serve solo per far girare l'app in locale.
- Le ipotesi elencate nel Contesto (modulo `flyway-database-postgresql`, Docker sul runner di GitHub) si verificano nella prima PR che introduce il database.

### Parere di Claude (opinione, non decisione)
- Parte 1: **Flyway**. Un solo database, schema piccolo, SQL leggibile e licenza Apache 2.0. Userei la versione gestita da Boot (12.4.0) e non la 13.x, per non uscire dal BOM.
- Parte 2: **Ibrido (opzione 3)**. I test non devono dipendere dallo stato di un altro repo; il compose resta il modo di far girare l'app. Costo da accettare: Docker Desktop acceso per eseguire `mvn verify` in locale.
- In entrambi i casi la versione di Postgres va fissata una volta sola e usata sia nei test sia nel compose. Quale versione è un dato da verificare quando si scrive il compose.

## Conseguenze
- Con Flyway, ogni modifica allo schema è un nuovo file SQL e i file già applicati non si toccano più. Passare a Liquibase dopo è possibile (Liquibase può partire da uno schema esistente) ma costa lavoro.
- Con Testcontainers, `mvn verify` in locale fallisce se Docker non è avviato. Si può separare i test di integrazione (plugin Failsafe, `*IT.java`) per lasciare veloci i test unitari: è una scelta di dettaglio da fare nella PR.
- Il compose di `platform-infra` diventa necessario per far girare l'app in locale, non per i test.
- Tornare indietro: cambiare la strategia dei test costa poco finché i test di integrazione sono pochi; cambiare lo strumento di migrazione costa di più dopo le prime migrazioni.
