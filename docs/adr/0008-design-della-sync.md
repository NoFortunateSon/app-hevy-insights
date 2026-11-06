# 0008 - Design della sync con Hevy

Stato: Proposta
Data: 2026-11-06

## Contesto
- L'ADR 0003 chiede una sync incrementale: primo caricamento completo, poi solo le modifiche; si salva il JSON originale (tabella `hevy_workout_raw`, ADR 0005) e, più avanti, le righe normalizzate.
- L'ADR 0007 dice che la sync **parte da `HevyApiController`**, che legge da Hevy e passa i dati a un service; il service salva tramite un repository.
- Oggi esiste solo la lettura da Hevy (`HevyApiController`: tutti i workout, oppure gli eventi da una data). Non esistono ancora service, repository né model.
- Restano aperti, dall'ADR 0005: da dove arriva l'UUID dell'unico utente e come si registrano i workout cancellati su Hevy.

Fatti dalla spec OpenAPI di Hevy (`https://api.hevyapp.com/docs/swagger-ui-init.js`, letta il 2026-11-06):
- `GET /v1/workouts/events`: "Retrieve a paged list of workout events (updates or deletes) since a given date. Events are ordered from newest to oldest." Scopo dichiarato: tenere aggiornata una copia locale "without having to fetch the entire list of workouts".
- `since`: stringa facoltativa, default `1970-01-01T00:00:00Z`, senza formato né descrizione.
- Evento `updated`: contiene il workout completo, senza una data propria dell'evento. Evento `deleted`: `id` obbligatorio, `deleted_at` facoltativo.
- `GET /v1/user/info` restituisce `data.id` ("The user ID.", nell'esempio ha la forma di un UUID), più nome, unità di misura e `is_pro`.
- Hevy chiede di non chiamare l'API "exactly at xx:00" per le sincronizzazioni periodiche.

**Non documentato** (quindi ipotesi da verificare sui dati reali di Alessio, con la sua chiave):
- con quale data si confronta `since` (`updated_at`? `deleted_at`?), e se è inclusivo;
- per quanto tempo Hevy tiene gli eventi;
- se `updated_at` cambia a ogni modifica del workout;
- se l'`id` utente è stabile nel tempo;
- se le pagine restano coerenti quando arrivano modifiche durante la lettura. Ipotesi: con l'ordine "dal più recente", un evento nuovo sposta gli altri in avanti e produce al massimo un duplicato, non un buco. Un duplicato non fa danni se il salvataggio è idempotente (aggiorna la riga se esiste).

## Opzioni

### A. Strategia di caricamento
1. **Primo carico con `/v1/workouts`, poi solo `/v1/workouts/events` da un punto di ripartenza.**
   - Pro: è l'uso dichiarato da Hevy; dopo il primo carico le richieste sono poche.
   - Contro: dipende da `since` e dalla durata degli eventi, che non sono documentati.
2. **Solo `/v1/workouts/events`, la prima volta con `since` = 1970.**
   - Pro: un solo percorso nel codice. Contro: non sappiamo se gli eventi più vecchi ci sono ancora.
3. **Ricarica completa con `/v1/workouts` a ogni sync**, con le cancellazioni ricavate confrontando gli id.
   - Pro: non dipende da `since`. Contro: con N workout servono N/10 richieste a ogni giro (ipotesi: con qualche centinaio di workout sono decine di chiamate).

Raccomandazione di Claude: **1**, con l'opzione 3 tenuta come ricarica manuale di emergenza se un giorno gli eventi non bastano.

### B. Punto di ripartenza
1. **Nuova tabella `hevy_sync_state`** (`user_id`, `last_sync_started_at`): si salva l'ora di **inizio** di ogni sync riuscita e la sync dopo chiede gli eventi da lì. Le modifiche avvenute durante la sync vengono rilette la volta dopo (sovrapposizione voluta, innocua con il salvataggio idempotente).
   - Pro: esplicito e indipendente dal significato di `updated_at`. Contro: migrazione V2 e una tabella in più.
2. **Ricavato dai dati**: il `hevy_updated_at` più recente in `hevy_workout_raw`.
   - Pro: nessuna tabella. Contro: ignora le cancellazioni e si basa su `updated_at`, il cui comportamento non è documentato.

Raccomandazione di Claude: **1**.

### C. Workout cancellati su Hevy
1. **Cancellazione logica**: colonna `deleted_at` in `hevy_workout_raw` (migrazione V2); le metriche ignorano le righe cancellate.
   - Pro: resta traccia; se Hevy manda un evento sbagliato, il dato non è perso. Contro: ogni query delle metriche deve filtrare.
2. **Cancellazione fisica** della riga.
   - Pro: semplice, le query non filtrano. Contro: dato perso; nessuna traccia di cosa è sparito.

Raccomandazione di Claude: **1**.

### D. UUID dell'unico utente
1. **Proprietà di configurazione** (`hevy-insights.user-id`, da variabile d'ambiente con un valore di default per l'uso locale).
   - Pro: esplicita; per il multi-utente diventa un parametro. Contro: un valore in più da impostare.
2. **Costante in `utils.Constants`.**
   - Pro: zero configurazione. Contro: da togliere col multi-utente; un UUID fisso nel codice.
3. **Id dell'utente Hevy** letto da `/v1/user/info`.
   - Pro: nessun valore inventato. Contro: lega il nostro modello a Hevy (stesso motivo per cui l'ADR 0005 non ha scelto l'opzione TEXT); stabilità dell'id non documentata; una chiamata in più.

Raccomandazione di Claude: **1**.

### E. Chi fa partire la sync
1. **Periodica** (`@Scheduled` dentro `HevyApiController`), per esempio ogni 6 ore a un minuto non tondo, come chiede Hevy; intervallo in `application.yaml`.
   - Pro: dati sempre aggiornati senza intervento. Contro: gira anche quando non serve.
2. **All'avvio dell'app.**
   - Pro: semplicissima. Contro: i dati invecchiano finché l'app resta accesa.
3. **Endpoint REST** (`POST /sync`).
   - Pro: si lancia quando serve. Contro: l'ADR 0003 vuole REST in sola lettura; servirebbe almeno una protezione (package `security`).

Raccomandazione di Claude: **1 e 2 insieme** (all'avvio e poi periodica), senza endpoint.

### Fuori da questo ADR
- **Tecnologia del repository** (JDBC con `JdbcClient`, Spring Data JDBC o JPA): ADR separato, da decidere prima di scrivere il codice della sync.
- **Righe normalizzate** (esercizi, serie): dopo, quando si definiscono le metriche.
- **Metrica "recovery"**: i workout non hanno tempi di recupero (verificato sulla spec); va ridefinita o tolta nell'ADR delle metriche.

## Decisione
(da compilare da Alessio)

## Conseguenze
Valgono se si scelgono le raccomandazioni; con altre scelte cambiano di conseguenza.
- Migrazione V2: tabella `hevy_sync_state` e colonna `deleted_at` in `hevy_workout_raw`. La V1 non si tocca (ADR 0004).
- Nuove classi, una per compito (regola 19), nei package dell'ADR 0007:
  - `controller`: in `HevyApiController` il punto d'avvio della sync (all'avvio e periodico), che legge da Hevy e passa i DTO al service;
  - `service`: un service della sync che decide primo carico o eventi, aggiorna il punto di ripartenza solo a sync riuscita;
  - `repository`: accesso a `hevy_workout_raw` (salvataggio idempotente, cancellazione logica) e a `hevy_sync_state`;
  - `model`: le righe delle due tabelle (primi model veri del progetto);
  - `config`: proprietà `hevy-insights.user-id` e intervallo della sync.
- Test: integrazione con Postgres reale (Testcontainers) e risposte Hevy registrate (`MockRestServiceServer`); nessuna chiamata alla vera API in CI.
- Prima di dichiarare la sync affidabile: una prova sui dati reali di Alessio per verificare le ipotesi elencate nel Contesto (significato di `since`, durata degli eventi, `updated_at`).
