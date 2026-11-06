# Stato del progetto app-hevy-insights

Ultimo aggiornamento: 2026-11-07 (PR per issue #50). Regola in `CLAUDE.md`, sezione "Status file".
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
| Sync con Hevy: JPA, migrazione V2, `HevySyncService`, `HevySyncController` (all'avvio e ogni 6 ore) | #35 | CI verde sulla PR (run 42274015768); 41 test in locale |
| Primo giro reale della sync registrato in questo file | #43 | Solo documentazione |
| ADR 0011 accettata; conferma delle classi `public` di `controller/hevy` | #45 | CI verde |
| JaCoCo, PIT (workflow settimanale), SonarQube Cloud in CI | #47 | CI verde su `main` (run 42297385212); prima analisi Sonar riuscita (vedi Verificato) |
| ADR 0012 (repository pubblico, configurazione GitHub) e 0013 (frontend separato, backend monolitico), template di PR e issue, `CODEOWNERS`, `SECURITY.md`, workflow `Dependency review` | #49 | CI verde su `main` (run 42303108304) |
| Lettura di `.env` in locale (`spring.config.import`), mai nei test | #37 | CI verde sulla PR (run 42276168661) |
| ADR 0010 accettata: GitHub Actions pianificato + Postgres Neon gratuito | #39 | CI verde sulla PR (run 42280114727) |
| Workflow `Hevy sync` ogni 6 ore (minuto 17 UTC) + modalità "una sync e poi esci" | #41 | CI verde sulla PR (run 42280746164); primo giro reale riuscito (vedi Verificato) |

## Verificato
- La CI usa Testcontainers con Postgres 18.6 sul runner GitHub (`ubuntu-latest`): funziona (run della PR #12).
- `flyway-database-postgresql` va dichiarato a parte (versione dal BOM di Boot): verificato nell'ADR 0005.
- API Hevy, dalla spec OpenAPI in `https://api.hevyapp.com/docs/swagger-ui-init.js` (letta il 2026-11-06): header `api-key`; `GET /v1/workouts` e `GET /v1/workouts/events` con `page` e `pageSize` (massimo 10); gli eventi hanno `since` e sono di tipo `updated` (workout completo) o `deleted` (`id`, `deleted_at` facoltativo), dal più recente.
- I workout dell'API **non** hanno tempi di recupero: `rest_seconds` esiste solo nelle routine (stessa spec).

- **Primo giro reale della sync** (2026-11-06, workflow `Hevy sync`, run 42284279135, avvio manuale): riuscito in 48 s compresa la compilazione; "first full load, 1 workouts". Conteggio confermato col connettore Hevy (`workout_count` = 1). Neon: PostgreSQL 18.6, endpoint diretto (senza `-pooler`), migrazioni V1 e V2 applicate da Flyway. I quattro secrets del repository li ha impostati Alessio.
- Recupero: nel workout reale (letto col connettore Hevy) le serie hanno solo `type`, `weight_kg`, `reps`, `rpe`, nessun campo di recupero; c'è `routine_id`, quindi è ricavabile solo il recupero pianificato della routine. Conferma definitiva sul JSON salvato in Neon: non ancora fatta.

- **SonarQube Cloud, prima analisi su `main`** (2026-11-06, letta via API): Quality Gate superato; 0 bug, 0 vulnerabilità, 0 security hotspot, 0 code smell; copertura 98,4% (righe 98,8%, rami 96,6%); duplicazioni 0%; 967 righe di codice. JaCoCo importato, Java 25 riconosciuto.
- **Repository pubblico dal 2026-11-06** (ADR 0012), dopo un controllo di sicurezza pulito su cronologia, messaggi, issue, PR e log. Attivi: ruleset `protect-main` (solo PR, job `build` verde, niente force push né cancellazione), secret scanning con push protection, CodeQL (default setup, suite estesa), aggiornamenti di sicurezza Dependabot, segnalazione privata delle vulnerabilità, auto-merge, cancellazione dei branch dopo il merge.

## Non verificato / da fare
- Linee guida (`docs/linee-guida.md`): basate solo su Ousterhout (sommario) e Fowler (cap. 2 e inizio cap. 3). Effective Java e Pragmatic Programmer (epub) non letti; Khorikov solo indice.
- Limiti di frequenza di Hevy: non documentati (niente 429 né `Retry-After` nella spec). La gestione del 429 nella PR #15 è difensiva, non provata contro Hevy.
- Con un solo workout su Hevy non sono ancora verificabili: paginazione su più pagine, eventi di modifica e cancellazione reali. Non documentati dalla spec; indicazioni di Alessio del 2026-11-06 usate come base, da confermare sui dati reali: `since` si confronta con la data della modifica; eventi tenuti 10 anni (per ora); `updated_at` cambia a ogni modifica; id utente Hevy stabile (per ora).
- ADR 0001 e 0002 del workspace, citate in `CLAUDE.md`: non presenti nel repo `workspace-docs` (su `origin/main` c'è solo il README, controllato il 2026-11-06).
- Maven wrapper (`mvnw`) assente.
- Spring Modulith / Spring AI: versione non ancora fissata nel `pom.xml`.
- Metrica "recovery": Alessio indica che Hevy ha il tempo di recupero. Nella spec il workout non ha campi di recupero; `rest_seconds` c'è solo nelle routine (recupero pianificato). Da verificare sul JSON reale (campi non documentati o routine collegata); si decide nell'ADR delle metriche.

## In corso
- Issue #50, branch `claude/50-quality-thresholds`: soglie bloccanti decise da Alessio il 2026-11-07 (ADR 0011, addendum): Quality Gate Sonar bloccante, JaCoCo minimo 90% righe e 85% rami, PIT minimo 90%, PIT anche su ogni PR. CI: vedi i check della PR.
- Per i prossimi progetti (ADR 0012): repository template privato `NoFortunateSon/template-spring-app` (CI verde, run 42301699357) e script `platform-infra/scripts/configure-github-repo.sh` (PR platform-infra #6, mergiata). Sui repository privati del piano Free l'auto-merge resta spento.
- Dopo: ADR delle metriche.

## Decisioni in attesa di Alessio
- La sync parte da `HevySyncController` (classe nuova) invece che da `HevyApiController`, per la regola 19: da confermare, oppure si accorpano (ADR 0008, sezione Implementazione).

## Per il coordinatore
- Nessuna richiesta.
