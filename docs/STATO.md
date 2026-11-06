# Stato del progetto app-hevy-insights

Ultimo aggiornamento: 2026-11-06 (PR per issue #40). Regola in `CLAUDE.md`, sezione "Status file".
Leggono questo file: Alessio e l'assistente di progetto su claude.ai. Solo fatti verificati; le ipotesi sono marcate.

## Fatto (su `main`)
| Cosa | PR / ADR | Verifica |
|---|---|---|
| Docs di progetto, `.gitignore` | #2 | Nessuna CI al momento della PR (il workflow arriva con #4) |
| Scheletro Maven, Spring Boot 4.1.2, Java 25, CI | #4 | CI verde su `main` |
| ADR 0004: Flyway + Testcontainers (accettata) | #6, #8 | CI verde |
| Dependabot per Maven e GitHub Actions | #10 | CI verde |
| Flyway, Testcontainers, tabella `hevy_workout_raw` (V1), ADR 0005 (accettata) | #12 | CI verde sulla PR e su `main`; 7 test in locale secondo la descrizione della PR |
| Regola del file di stato, `docs/STATO.md` | #16 | Solo documentazione |
| Client Hevy (`HevyClient`) in sola lettura (workout ed eventi, paginazione, retry su 429), contract test; README, `.gitattributes` | #15 | CI verde sulla PR e su `main` (run 42256422866) |
| Linee guida di codice `docs/linee-guida.md`, ADR 0006 proposta | #18 | CI verde su `main` (run 42257789483) |
| ADR 0006 accettata (opzione 3, ibrida), regola 18, client Hevy adeguato alle linee guida (`HevyApiException.statusCode()` ora `OptionalInt`) | #20 | CI verde sulla PR (run 42258472439) e su `main` (run 42260149343) |
| ADR 0007 accettata: package `controller` → `service` → `repository`, più `exception`, `config`, `model` (`dto`), `security`, `utils`; `LayerDependencyTests` | #23 | CI verde su `main` (run 42265118957) |
| Classi rinominate per ruolo; client Hevy = `controller/HevyApiController` (il repository è solo database); criterio model/DTO | #25 | CI verde su `main` (run 42265157454) |
| Regola 19 "una classe, un compito", aggiunta all'ADR 0007 | #28 | CI verde su `main` (run 42266731836) |
| `HevyApiController` diviso per compiti: `controller/hevy` (`HevyHttpClient`, `HevyPageWalker`, `HevyResponseMapper`), `utils/RateLimitRetryPolicy`; nessun cambio di comportamento | #29 | CI verde sulla PR (run 42268241973) e su `main` (run 42269874487); 34 test in locale |
| ADR 0008 proposta: design della sync | #31 | CI verde su `main` (run 42270489208); solo documentazione |
| ADR 0008 accettata (A1 B1 C1 D1 E1+E2), indicazioni di Alessio sui punti non documentati; ADR 0009 accettata (JPA) | #33 | Solo documentazione |
| Sync con Hevy: JPA, migrazione V2, `HevySyncService`, `HevySyncController` (all'avvio e ogni 6 ore) | #35 | CI verde sulla PR (run 42274015768); 41 test in locale. Mai eseguita contro la vera API Hevy |

## Verificato
- La CI usa Testcontainers con Postgres 18.6 sul runner GitHub (`ubuntu-latest`): funziona (run della PR #12).
- `flyway-database-postgresql` va dichiarato a parte (versione dal BOM di Boot): verificato nell'ADR 0005.
- API Hevy, dalla spec OpenAPI in `https://api.hevyapp.com/docs/swagger-ui-init.js` (letta il 2026-11-06): header `api-key`; `GET /v1/workouts` e `GET /v1/workouts/events` con `page` e `pageSize` (massimo 10); gli eventi hanno `since` e sono di tipo `updated` (workout completo) o `deleted` (`id`, `deleted_at` facoltativo), dal più recente.
- I workout dell'API **non** hanno tempi di recupero: `rest_seconds` esiste solo nelle routine (stessa spec).

## Non verificato / da fare
- Linee guida (`docs/linee-guida.md`): basate solo su Ousterhout (sommario) e Fowler (cap. 2 e inizio cap. 3). Effective Java e Pragmatic Programmer (epub) non letti; Khorikov solo indice.
- Limiti di frequenza di Hevy: non documentati (niente 429 né `Retry-After` nella spec). La gestione del 429 nella PR #15 è difensiva, non provata contro Hevy.
- Nessuna chiamata alla vera API Hevy finora (la sync è pronta ma non è mai stata eseguita sui dati reali): forma delle risposte verificata solo sulla spec, non su dati reali. Non documentati dalla spec; indicazioni di Alessio del 2026-11-06 usate come base, da confermare sui dati reali: `since` si confronta con la data della modifica; eventi tenuti 10 anni (per ora); `updated_at` cambia a ogni modifica; id utente Hevy stabile (per ora).
- ADR 0001 e 0002 del workspace, citate in `CLAUDE.md`: non presenti nel repo `workspace-docs` (su `origin/main` c'è solo il README, controllato il 2026-11-06).
- Maven wrapper (`mvnw`) assente.
- Spring Modulith / Spring AI: versione non ancora fissata nel `pom.xml`.
- Metrica "recovery": Alessio indica che Hevy ha il tempo di recupero. Nella spec il workout non ha campi di recupero; `rest_seconds` c'è solo nelle routine (recupero pianificato). Da verificare sul JSON reale (campi non documentati o routine collegata); si decide nell'ADR delle metriche.

## In corso
- Issue #36, branch `claude/36-load-dotenv`: l'app legge `.env` della cartella del progetto se esiste (`spring.config.import: optional:file:${HEVY_INSIGHTS_ENV_FILE:.env}[.properties]`); le variabili d'ambiente vere vincono (verificato sulla documentazione di Spring Boot 4.1.2). I test non leggono mai `.env` (Surefire punta `HEVY_INSIGHTS_ENV_FILE` a un file inesistente). Verificato: 44 test verdi in locale; avvio del jar con il `.env` di Alessio e sync spenta, porta 8099: health `UP`, Flyway "Schema is up to date". Effetto collaterale: la migrazione V2 è stata applicata al database locale di Alessio. CI: vedi i check della PR.
- Issue #38, branch `claude/38-adr-run-environment` (parte dal branch della #37): ADR 0010 **accettata**: GitHub Actions pianificato + Postgres Neon gratuito (opzioni verificate sulle pagine ufficiali del 2026-11-06). Solo documentazione.

- Issue #40, branch `claude/40-scheduled-sync-workflow` (parte dal branch della #39): workflow `Hevy sync` su GitHub Actions ogni 6 ore + modalità "una sync e poi esci" (`HevySyncOnceRunner`); `since` troncato al secondo. 47 test verdi in locale; comando del workflow provato in locale contro Postgres temporaneo e finto Hevy (exit 0 su successo, 1 su errore 500). CI: vedi i check della PR. **Non ancora attivo:** servono il progetto Neon e i 4 secrets del repository (passi nel README), da fare ad Alessio.

## Decisioni in attesa di Alessio
- La sync parte da `HevySyncController` (classe nuova) invece che da `HevyApiController`, per la regola 19: da confermare, oppure si accorpano (ADR 0008, sezione Implementazione).
- Classi di supporto di `controller/hevy` `public` invece che package-private: Java non permette altro finché `HevyApiController` sta in `controller` (dettagli nell'ADR 0007, esito dell'issue #27). Da confermare, oppure spostare `HevyApiController` in `controller/hevy`.

## Per il coordinatore
- Nessuna richiesta.
