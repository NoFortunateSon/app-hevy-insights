# Stato del progetto app-hevy-insights

Ultimo aggiornamento: 2026-11-06 (PR #14). Regola in `CLAUDE.md`, sezione "Status file".
Leggono questo file: Alessio e l'assistente di progetto su claude.ai. Solo fatti verificati; le ipotesi sono marcate.

## Fatto (su `main`)
| Cosa | PR / ADR | Verifica |
|---|---|---|
| Docs di progetto, `.gitignore` | #2 | Nessuna CI al momento della PR (il workflow arriva con #4) |
| Scheletro Maven, Spring Boot 4.1.2, Java 25, CI | #4 | CI verde su `main` |
| ADR 0004: Flyway + Testcontainers (accettata) | #6, #8 | CI verde |
| Dependabot per Maven e GitHub Actions | #10 | CI verde |
| Flyway, Testcontainers, tabella `hevy_workout_raw` (V1), ADR 0005 (accettata) | #12 | CI verde sulla PR e su `main`; 7 test in locale secondo la descrizione della PR |

## Verificato
- La CI usa Testcontainers con Postgres 18.6 sul runner GitHub (`ubuntu-latest`): funziona (run della PR #12).
- `flyway-database-postgresql` va dichiarato a parte (versione dal BOM di Boot): verificato nell'ADR 0005.

## Non verificato / da fare
- Modulo `hevyclient` (issue #13): non iniziato su `main`; l'ADR per sync incrementale (endpoint eventi o elenco completo) non esiste ancora.
- Maven wrapper (`mvnw`) assente.
- README ancora minimo.
- Da dove arriva l'UUID dell'unico utente (configurazione o costante): da decidere.
- Gestione dei workout cancellati su Hevy: da decidere.
- Spring Modulith / Spring AI: versione non ancora fissata nel `pom.xml`.
- Recupero tra le serie: non sappiamo se l'API lo espone (serve la chiave Hevy e i dati reali).

## In corso
- Issue #13 `hevyclient` (aperta).

## Decisioni in attesa di Alessio
- Nessuna ADR in stato `Proposta` al momento.

## Per il coordinatore
- Nessuna richiesta.
