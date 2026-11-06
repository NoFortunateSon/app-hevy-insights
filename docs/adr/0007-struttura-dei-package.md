# 0007 - Struttura dei package: controller, service, repository

Stato: Accettata
Data: 2026-11-06

## Contesto
- L'ADR 0003 propone un monolite modulare con quattro moduli per responsabilità (`hevyclient`, `sync`, `metrics`, `api`), dichiarandolo "da rivedere nel primo ciclo di lavoro".
- Alessio ha chiesto di passare alla struttura a livelli classica di Spring. A oggi c'è codice solo nel client Hevy e nella migrazione V1, quindi lo spostamento costa poco.
- Le linee guida (`docs/linee-guida.md`) vanno in tensione con questa struttura: regola 2 (una decisione in un solo modulo) e regola 3 (niente livelli che inoltrano soltanto). Per la regola di `CLAUDE.md` lo scontro va deciso in un ADR.
- Fatto sulle prestazioni: i package Java non hanno costo a runtime; un livello in più è al massimo una chiamata di metodo, trascurabile rispetto a una chiamata HTTP a Hevy o a una query Postgres. Non misurato su questo progetto.

## Opzioni
1. **Moduli per responsabilità (ADR 0003).** Pro: ogni decisione sta in un modulo. Contro: struttura meno familiare.
2. **Tre livelli astratti** (`presentation`, `business`, `data`). Proposta intermedia di questa stessa PR, superata dalla decisione sotto.
3. **Struttura classica di Spring**: livelli `controller`, `service`, `repository` più package trasversali per tipo di classe.

## Decisione
Deciso da Alessio il 2026-11-06: **opzione 3**.

| Package | Contenuto |
|---|---|
| (radice) | `HevyInsightsApplication`, la classe `main` |
| `controller` | Confine HTTP dell'app: espone i service (riceve le richieste, valida l'input, restituisce le risposte) e parla con l'API Hevy (`HevyApiController`). |
| `service` | Logica di business: elabora i dati dei controller, coordina i repository. |
| `repository` | Solo accesso al database. |
| `exception` | Tutte le eccezioni. |
| `config` | Configurazioni Spring e proprietà di configurazione. |
| `model`, `model/dto` | Modelli; i DTO nella sottocartella `dto` (per ora `HevyWorkoutDto`, `HevyWorkoutEventDto`). |
| `security` | Tutto ciò che riguarda la sicurezza (per ora vuoto). |
| `utils` | La classe `Constants` e le classi che non rientrano negli altri package (per ora `RetrySleeper`). |

Regole di dipendenza (controllate da `LayerDependencyTests`):
- `controller` → `service` → `repository`, mai al contrario e senza saltare livelli (un controller non usa un repository).
- `model`, `exception`, `utils` sono usabili da tutti e non dipendono da nessun altro package dell'app.
- `config` e `security` possono usare tutti i livelli (servono a collegarli).

Scelte di Claude nell'applicare la decisione (da correggere se Alessio non è d'accordo):
- Client dell'API Hevy: deciso da Alessio il 2026-11-06 (issue #24), dopo una prima collocazione in `repository`: si chiama `HevyApiController` e sta in `controller`, perché il repository è solo database. **La sync parte da `HevyApiController`**: legge da Hevy e passa i dati a un service, che li salva tramite un repository. Così il verso `controller` → `service` → `repository` resta valido.
  - Nota tecnica (di Claude): in Spring "controller" indica di solito una classe che *riceve* richieste HTTP (`@RestController`); `HevyApiController` invece *invia* richieste a Hevy e non è un endpoint REST. Il nome segue la scelta di Alessio.
  - Conseguenza: la parte della logica che collega Hevy e database (quando leggere, cosa passare al service) sta nel controller, non solo nel service.
- `HevyWorkoutDto` e `HevyWorkoutEventDto` in `model/dto`: sono dati che arrivano da Hevy.
- Model o DTO: un **model** è un dato nostro (riga di una tabella, oggetto di dominio con le sue regole); un **DTO** trasporta dati attraverso un confine (da Hevy, o verso l'esterno via REST), non ha logica e il nome finisce in `Dto`. Per questo i dati di Hevy sono DTO; il primo model sarà la riga di `hevy_workout_raw`.
- Nomi delle classi (issue #24): ogni classe dice il suo ruolo (`...Repository`, `...Configuration`, `...Dto`, `...Exception`).
- In `Constants` vanno i valori fissi del client Hevy (header, dimensione pagina, percorsi, attese dopo un 429). I nomi dei campi JSON di Hevy restano in `HevyApiController`, l'unica classe che legge quel JSON.

## Conseguenze
- Regola 2 delle linee guida e regola di `CLAUDE.md` sulla forma dell'API Hevy: il JSON di Hevy lo legge solo `HevyApiController`; header, percorsi e dimensione pagina stanno in `utils.Constants`. La conoscenza di Hevy è quindi in due punti invece che in uno: è il costo accettato di questa struttura.
- Regola 3: un service che inoltra soltanto a un repository è accettato come costo dei livelli. Se succede per molte funzioni, se ne riparla in un nuovo ADR.
- Diverse classi che prima erano visibili solo nel loro package ora sono `public` (costruttore di `HevyApiController`, eccezioni, `RetrySleeper`, `HevyApiProperties.hasKey()`), perché lavorano tra package diversi.
- Il controllo delle dipendenze è testuale (import e nomi completi), senza librerie nuove. Una libreria dedicata (ArchUnit, Spring Modulith) sarebbe una nuova dipendenza: serve un ADR.
- Aggiornati `CLAUDE.md` (Architecture, Testing), `README.md`, `docs/linee-guida.md` (regole 2 e 3), `docs/STATO.md`, nota nell'ADR 0003. Gli ADR 0003 e 0005 non si riscrivono: i nomi `hevyclient` e `sync` lì sono storici.

## Aggiunta del 2026-11-06 (una classe, un compito)
Richiesta da Alessio: il codice deve essere diviso per compiti, non concentrato in poche classi. Oggi `HevyApiController` (218 righe) fa insieme la chiamata HTTP, lo scorrimento delle pagine, la gestione del 429 e la lettura del JSON.
- Le classi di supporto di un livello vanno in un sotto-package di quel livello (per ora `controller/hevy`), non pubbliche. Il livello espone un punto d'ingresso piccolo (`HevyApiController`). Le regole di dipendenza e `LayerDependencyTests` valgono anche per i sotto-package.
- Il JSON di Hevy lo leggerà quindi `HevyResponseMapper` (in `controller/hevy`), non più solo `HevyApiController`: la conoscenza della forma dell'API Hevy resta dentro `controller/hevy`. Header, percorsi e dimensione pagina restano in `utils.Constants`.
- `RateLimitRetryPolicy` (quando riprovare dopo un 429) va in `utils`: è logica pura, senza dipendenze dagli altri package.
- Il nome `HevyApiController` non cambia (scelta di Alessio, issue #24).
- Questa aggiunta vale dopo il merge della PR che la contiene; il lavoro sul codice è l'issue #27.
