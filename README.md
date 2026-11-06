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
Copia `.env.example` in `.env` nella cartella del progetto (ignorato da Git) e riempi i valori dal gestore di password. L'app legge `.env` all'avvio se lo trova nella cartella da cui parte (`spring.config.import` in `application.yaml`); le variabili d'ambiente vere hanno la precedenza, quindi in un ambiente di esecuzione basta impostarle lì. I test non leggono mai `.env`.

| Variabile | Uso |
|---|---|
| `HEVY_API_KEY` | Chiave API Hevy. È una password: mai nei log, nei test o in Git. |
| `HEVY_INSIGHTS_USER_ID` | Facoltativa: UUID dell'unico utente (default in `application.yaml`). |
| `HEVY_INSIGHTS_DB_PASSWORD` | Password dell'utente `hevy_insights` del Postgres di `platform-infra`. |

L'app parte anche senza `HEVY_API_KEY`: la chiave viene controllata solo quando si chiama Hevy.

## Comandi
- Build e test: `mvn verify` (avvia Postgres 18.6 con Testcontainers; nessuna chiamata alla vera API Hevy).
- Avvio locale: prima `docker compose up -d --wait` in `platform-infra`, poi `mvn spring-boot:run` dalla cartella del progetto (serve per trovare `.env`). Porta 8080, health su `/actuator/health`. Con `HEVY_API_KEY` impostata, la sync con Hevy parte 30 secondi dopo l'avvio.

## Esecuzione a PC spento (ADR 0010)
La sync gira ogni 6 ore (minuto 17, UTC) sul workflow GitHub Actions `Hevy sync` (`.github/workflows/hevy-sync.yml`), in modalità "una sync e poi esci", e scrive su un Postgres gratuito di Neon. Si può lanciare anche a mano da GitHub: Actions → Hevy sync → Run workflow.

Configurazione una tantum (la fa Alessio; i valori non passano mai dal repository):
1. Su [neon.com](https://neon.com) crea un progetto gratuito (regione europea; Postgres 18 se disponibile). Dalla "connection string" `postgresql://UTENTE:PASSWORD@HOST/DATABASE?sslmode=require` ricava i tre valori sotto.
2. Su GitHub: repository → Settings → Secrets and variables → Actions → New repository secret, quattro secrets:

| Secret | Valore |
|---|---|
| `HEVY_API_KEY` | La chiave API Hevy |
| `NEON_JDBC_URL` | `jdbc:postgresql://HOST/DATABASE?sslmode=require` |
| `NEON_DB_USER` | `UTENTE` |
| `NEON_DB_PASSWORD` | `PASSWORD` |

Finché `HEVY_API_KEY` e `NEON_JDBC_URL` mancano, il workflow si salta con un avviso invece di fallire. Le tabelle su Neon le crea Flyway al primo giro. I dati su Neon sono separati da quelli del Postgres locale.

## Struttura
Livelli classici di Spring (ADR 0007): `controller` → `service` → `repository`, mai al contrario e senza saltare livelli; lo controlla `LayerDependencyTests`.

| Package | Cosa contiene | Oggi |
|---|---|---|
| (radice) | La classe `main` | `HevyInsightsApplication` |
| `controller` | Confine HTTP: espone i service (REST in sola lettura), parla con l'API Hevy, avvia la sync | `HevyApiController` (punto d'ingresso API Hevy, sola lettura; in `controller/hevy`: `HevyHttpClient`, `HevyPageWalker`, `HevyResponseMapper`), `HevySyncController` (sync all'avvio e ogni 6 ore) |
| `service` | Logica di business: sync incrementale, metriche | `HevySyncService` |
| `repository` | Solo accesso al database (JPA, ADR 0009) | `HevyWorkoutRawRepository`, `HevySyncStateRepository`; migrazioni V1, V2 |
| `exception` | Tutte le eccezioni | `HevyApiException`, `HevyRateLimitExceededException` |
| `config` | Configurazioni | `ApplicationConfiguration`, `HevyInsightsProperties`, `HevyApiConfiguration`, `HevyApiProperties`, `HevySyncSchedulingConfiguration` |
| `model`, `model/dto` | Modelli; DTO nella sottocartella | `HevyWorkoutRaw`, `HevyWorkoutRawId`, `HevySyncState` (entità JPA); `HevyWorkoutDto`, `HevyWorkoutEventDto` (DTO) |
| `security` | Sicurezza | Vuoto |
| `utils` | `Constants` e classi che non rientrano altrove | `Constants`, `RetrySleeper`, `RateLimitRetryPolicy` |

Migrazioni Flyway in `src/main/resources/db/migration`. Una migrazione già applicata non si modifica: se ne aggiunge una nuova.

## Decisioni (ADR)
In `docs/adr/`, in italiano. La numerazione parte da 0003 perché 0001 e 0002 sono ADR del workspace.

| ADR | Titolo | Stato |
|---|---|---|
| [0003](docs/adr/0003-primo-progetto-hevy-insights.md) | Primo progetto: analisi dei dati Hevy | Accettata |
| [0004](docs/adr/0004-migrazioni-db-e-test-di-integrazione.md) | Migrazioni del database e test di integrazione | Accettata |
| [0005](docs/adr/0005-prima-tabella-workout-grezzi.md) | Prima tabella: workout grezzi e tipo di `user_id` | Accettata |
| [0006](docs/adr/0006-dimensione-delle-funzioni.md) | Dimensione delle funzioni | Accettata |
| [0007](docs/adr/0007-struttura-dei-package.md) | Struttura dei package: controller, service, repository | Accettata |
| [0008](docs/adr/0008-design-della-sync.md) | Design della sync con Hevy | Accettata |
| [0009](docs/adr/0009-tecnologia-del-repository.md) | Tecnologia del repository: JPA | Accettata |
| [0010](docs/adr/0010-ambiente-di-esecuzione-gratuito.md) | Ambiente di esecuzione gratuito, attivo a PC spento | Accettata |
| [0011](docs/adr/0011-qualita-del-codice-in-ci.md) | Qualità del codice in CI: SonarQube e strumenti di test | Accettata |
