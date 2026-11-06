# Stato del progetto app-hevy-insights

Ultimo aggiornamento: 2026-11-06 (PR per issue #32). Regola in `CLAUDE.md`, sezione "Status file".
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
| ADR 0008 `Proposta`: design della sync | #31 | CI verde su `main` (run 42270489208); solo documentazione |

## Verificato
- La CI usa Testcontainers con Postgres 18.6 sul runner GitHub (`ubuntu-latest`): funziona (run della PR #12).
- `flyway-database-postgresql` va dichiarato a parte (versione dal BOM di Boot): verificato nell'ADR 0005.
- API Hevy, dalla spec OpenAPI in `https://api.hevyapp.com/docs/swagger-ui-init.js` (letta il 2026-11-06): header `api-key`; `GET /v1/workouts` e `GET /v1/workouts/events` con `page` e `pageSize` (massimo 10); gli eventi hanno `since` e sono di tipo `updated` (workout completo) o `deleted` (`id`, `deleted_at` facoltativo), dal più recente.
- I workout dell'API **non** hanno tempi di recupero: `rest_seconds` esiste solo nelle routine (stessa spec).

## Non verificato / da fare
- Linee guida (`docs/linee-guida.md`): basate solo su Ousterhout (sommario) e Fowler (cap. 2 e inizio cap. 3). Effective Java e Pragmatic Programmer (epub) non letti; Khorikov solo indice.
- Limiti di frequenza di Hevy: non documentati (niente 429 né `Retry-After` nella spec). La gestione del 429 nella PR #15 è difensiva, non provata contro Hevy.
- Nessuna chiamata alla vera API Hevy finora: forma delle risposte verificata solo sulla spec, non su dati reali. Non documentati dalla spec; indicazioni di Alessio del 2026-11-06 usate come base, da confermare sui dati reali: `since` si confronta con la data della modifica; eventi tenuti 10 anni (per ora); `updated_at` cambia a ogni modifica; id utente Hevy stabile (per ora).
- ADR 0001 e 0002 del workspace, citate in `CLAUDE.md`: non presenti nel repo `workspace-docs` (su `origin/main` c'è solo il README, controllato il 2026-11-06).
- Maven wrapper (`mvnw`) assente.
- Spring Modulith / Spring AI: versione non ancora fissata nel `pom.xml`.
- Metrica "recovery": Alessio indica che Hevy ha il tempo di recupero. Nella spec il workout non ha campi di recupero; `rest_seconds` c'è solo nelle routine (recupero pianificato). Da verificare sul JSON reale (campi non documentati o routine collegata); si decide nell'ADR delle metriche.

## In corso
- Issue #32, branch `claude/32-sync-answers-jpa`: nell'ADR 0008 le indicazioni di Alessio sui punti non documentati da Hevy e sul recupero; ADR 0009 accettata (JPA; starter verificati nel BOM di Boot 4.1.2, Hibernate 7.4.13.Final; dipendenze da aggiungere con la sync). Solo documentazione. Nota: la PR #31 era già mergiata quando questo commit è stato pubblicato, da qui la nuova issue.

## Decisioni in attesa di Alessio
- **ADR 0008 `Proposta` (issue #30): design della sync.** Cinque scelte (A-E): strategia di caricamento, punto di ripartenza, cancellazioni, UUID dell'unico utente, chi fa partire la sync. Raccomandazioni di Claude nell'ADR; la Decisione è vuota. Alessio ha già dato le indicazioni sui punti non documentati da Hevy (vedi sotto).
- Classi di supporto di `controller/hevy` `public` invece che package-private: Java non permette altro finché `HevyApiController` sta in `controller` (dettagli nell'ADR 0007, esito dell'issue #27). Da confermare, oppure spostare `HevyApiController` in `controller/hevy`.

## Per il coordinatore
- Nessuna richiesta.
