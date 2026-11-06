# Stato del progetto app-hevy-insights

Ultimo aggiornamento: 2026-11-06 (PR per issue #19). Regola in `CLAUDE.md`, sezione "Status file".
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
| `HevyClient` in sola lettura (workout ed eventi, paginazione, retry su 429), contract test; README, `.gitattributes` | #15 | CI verde sulla PR e su `main` (run 42256422866) |
| Linee guida di codice `docs/linee-guida.md`, ADR 0006 proposta | #18 | CI verde su `main` (run 42257789483) |

## Verificato
- La CI usa Testcontainers con Postgres 18.6 sul runner GitHub (`ubuntu-latest`): funziona (run della PR #12).
- `flyway-database-postgresql` va dichiarato a parte (versione dal BOM di Boot): verificato nell'ADR 0005.
- API Hevy, dalla spec OpenAPI in `https://api.hevyapp.com/docs/swagger-ui-init.js` (letta il 2026-11-06): header `api-key`; `GET /v1/workouts` e `GET /v1/workouts/events` con `page` e `pageSize` (massimo 10); gli eventi hanno `since` e sono di tipo `updated` (workout completo) o `deleted` (`id`, `deleted_at` facoltativo), dal più recente.
- I workout dell'API **non** hanno tempi di recupero: `rest_seconds` esiste solo nelle routine (stessa spec).

## Non verificato / da fare
- Linee guida (`docs/linee-guida.md`): basate solo su Ousterhout (sommario) e Fowler (cap. 2 e inizio cap. 3). Effective Java e Pragmatic Programmer (epub) non letti; Khorikov solo indice.
- Limiti di frequenza di Hevy: non documentati (niente 429 né `Retry-After` nella spec). La gestione del 429 nella PR #15 è difensiva, non provata contro Hevy.
- Nessuna chiamata alla vera API Hevy finora: forma delle risposte verificata solo sulla spec, non su dati reali.
- ADR 0001 e 0002 del workspace, citate in `CLAUDE.md`: non presenti nel repo `workspace-docs` (su `origin/main` c'è solo il README, controllato il 2026-11-06).
- Maven wrapper (`mvnw`) assente.
- Da dove arriva l'UUID dell'unico utente (configurazione o costante): da decidere.
- Gestione dei workout cancellati su Hevy: da decidere.
- Spring Modulith / Spring AI: versione non ancora fissata nel `pom.xml`.
- Metrica "recovery": i tempi di recupero tra le serie non ci sono nei workout; va ridefinita o tolta (decisione di Alessio).

## In corso
- Issue #19, branch `claude/19-apply-guidelines`: ADR 0006 accettata (opzione 3, ibrida) e regola 18 in `docs/linee-guida.md`; `hevyclient` adeguato alle linee guida (niente valori magici, stato HTTP come `OptionalInt` al posto di -1, metodi privati estratti per nome, URL non duplicati nei test). Interfaccia pubblica invariata salvo `HevyApiException.statusCode()` (ora `OptionalInt`). 17 test verdi in locale con `mvn verify`; CI: vedi i check della PR.

## Decisioni in attesa di Alessio
- Nessuna ADR in stato `Proposta` (la 0006 è decisa: opzione 3).
- Prossima proposta prevista: design della sync (caricamento completo + eventi, punto di ripartenza, cancellazioni, UUID utente, recovery).

## Per il coordinatore
- Nessuna richiesta.
