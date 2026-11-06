# 0007 - Architettura a tre livelli

Stato: Accettata
Data: 2026-11-06

## Contesto
- L'ADR 0003 propone un monolite modulare con quattro moduli per responsabilità (`hevyclient`, `sync`, `metrics`, `api`), dichiarandolo "da rivedere nel primo ciclo di lavoro".
- Alessio ha chiesto se abbia senso dividere il progetto a tre livelli (presentazione, logica, dati). A oggi c'è codice solo nel client Hevy e nella migrazione V1, quindi lo spostamento costa poco.
- Le linee guida (`docs/linee-guida.md`) vanno in tensione con i livelli: regola 2 (una decisione in un solo modulo) e regola 3 (niente livelli che inoltrano soltanto). Per la regola di `CLAUDE.md` lo scontro va deciso in un ADR.
- Fatto sulle prestazioni: i package Java non hanno costo a runtime; un livello in più è al massimo una chiamata di metodo, che la JVM di solito rende trascurabile rispetto a una chiamata HTTP a Hevy o a una query Postgres (millisecondi). Non misurato su questo progetto.

## Opzioni
1. **Moduli per responsabilità (ADR 0003, com'è oggi).**
   - Pro: ogni decisione sta in un modulo; nessun livello che inoltra.
   - Contro: struttura meno familiare a chi conosce lo schema classico di Spring.
2. **Tre livelli al primo livello di package**, con sotto-package per area: `presentation`, `business` (`sync`, `metrics`), `data` (`hevy`, `db`).
   - Pro: struttura classica e prevedibile; il verso delle dipendenze è evidente.
   - Contro: una funzionalità attraversa tre package; alcuni servizi potrebbero solo inoltrare al livello dati.
3. **Moduli, con livelli interni solo dove servono.**
   - Pro: meno frammentazione. Contro: due strutture da imparare.

## Decisione
Deciso da Alessio il 2026-11-06: **opzione 2, tre livelli**, a condizione che non rallenti troppo l'esecuzione (vedi il fatto sulle prestazioni nel Contesto).

```
io.github.nofortunateson.hevyinsights
├── presentation      REST in sola lettura
├── business
│   ├── sync          sync incrementale
│   └── metrics       metriche dai soli dati salvati
└── data
    ├── hevy          client dell'API Hevy (ex hevyclient)
    └── db            accesso alle tabelle Postgres
```

Regole di dipendenza (controllate da `LayerDependencyTests`):
- Solo verso il basso e senza saltare livelli: `presentation` → `business` → `data`.
- `data.hevy` e `data.db` non si conoscono: il JSON di Hevy arriva al database solo passando dal livello `business`.
- `business.metrics` non usa `data.hevy` (le metriche non chiamano mai Hevy, ADR 0003).

## Conseguenze
- Regola 2 delle linee guida: resta valida dentro il livello dati. La forma dell'API Hevy la conosce solo `data.hevy`; gli altri package usano i suoi tipi (`HevyWorkout`, `HevyWorkoutEvent`), non il JSON.
- Regola 3 delle linee guida: con livelli rigidi un servizio di `business` può limitarsi a inoltrare al livello dati. È accettato come costo di questa scelta; se un servizio fa solo da passacarte per molte funzioni, se ne riparla in un nuovo ADR (per esempio permettendo a `presentation` di leggere direttamente da `data.db`).
- Il controllo delle dipendenze è testuale (import e nomi completi), senza librerie nuove. Una libreria dedicata (ArchUnit, Spring Modulith) sarebbe una nuova dipendenza: serve un ADR.
- Aggiornati `CLAUDE.md` (sezione Architecture), `README.md`, `docs/linee-guida.md` (regole 2 e 3), `docs/STATO.md`. Gli ADR 0003 e 0005 non si riscrivono: i nomi `hevyclient` e `sync` lì sono storici.
