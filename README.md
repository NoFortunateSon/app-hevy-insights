# app-hevy-insights
Analisi dei dati Hevy: carichi, volume, frequenza, recuperi, misure.

Servizio personale che sincronizza i workout dall'API pubblica di Hevy (solo lettura) in un Postgres locale e ne calcola le metriche.
Stato attuale del progetto: [`docs/STATO.md`](docs/STATO.md). Regole di lavoro per Claude Code: [`CLAUDE.md`](CLAUDE.md).

## Prerequisiti
- JDK 25 (build OpenJDK). Se `JAVA_HOME` punta a un JDK più vecchio, Maven fallisce con `release version 25 not supported`.
- Maven 3.9 (il wrapper `mvnw` non c'è ancora).
- Docker Desktop acceso: serve ai test (Testcontainers) e al Postgres locale.
- Per la sync: una chiave API Hevy (solo utenti Hevy Pro, da https://hevy.com/settings?developer).

## Configurazione
Copia `.env.example` in `.env` (ignorato da Git) e riempi i valori dal gestore di password:

| Variabile | Uso |
|---|---|
| `HEVY_API_KEY` | Chiave API Hevy. È una password: mai nei log, nei test o in Git. |
| `HEVY_INSIGHTS_DB_PASSWORD` | Password dell'utente `hevy_insights` del Postgres di `platform-infra`. |

L'app parte anche senza `HEVY_API_KEY`: la chiave viene controllata solo quando si chiama Hevy.

## Comandi
- Build e test: `mvn verify` (avvia Postgres 18.6 con Testcontainers; nessuna chiamata alla vera API Hevy).
- Avvio locale: prima `docker compose up -d --wait` in `platform-infra`, poi `mvn spring-boot:run`. Porta 8080, health su `/actuator/health`.

## Struttura
Tre livelli (ADR 0007). Le dipendenze vanno solo verso il basso, `presentation` → `business` → `data`, senza saltare livelli; lo controlla `LayerDependencyTests`.

| Package | Cosa fa | Stato |
|---|---|---|
| `presentation` | REST in sola lettura sulle metriche. | Vuoto |
| `business.sync` | Sync incrementale; salva il JSON originale e le righe normalizzate. | Vuoto |
| `business.metrics` | Metriche calcolate solo dai dati salvati; non chiama mai Hevy. | Vuoto |
| `data.hevy` | Chiamate all'API Hevy, paginazione, gestione HTTP 429. Unico package che conosce la forma dell'API. | Workout ed eventi dei workout |
| `data.db` | Accesso alle tabelle Postgres. | Solo tabella `hevy_workout_raw` (migrazione V1) |

Migrazioni Flyway in `src/main/resources/db/migration`. Una migrazione già applicata non si modifica: se ne aggiunge una nuova.

## Decisioni (ADR)
In `docs/adr/`, in italiano. La numerazione parte da 0003 perché 0001 e 0002 sono ADR del workspace.

| ADR | Titolo | Stato |
|---|---|---|
| [0003](docs/adr/0003-primo-progetto-hevy-insights.md) | Primo progetto: analisi dei dati Hevy | Accettata |
| [0004](docs/adr/0004-migrazioni-db-e-test-di-integrazione.md) | Migrazioni del database e test di integrazione | Accettata |
| [0005](docs/adr/0005-prima-tabella-workout-grezzi.md) | Prima tabella: workout grezzi e tipo di `user_id` | Accettata |
| [0006](docs/adr/0006-dimensione-delle-funzioni.md) | Dimensione delle funzioni | Accettata |
| [0007](docs/adr/0007-architettura-a-tre-livelli.md) | Architettura a tre livelli | Accettata |
