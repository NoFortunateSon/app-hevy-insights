# 0012 - Repository pubblico e configurazione di GitHub

Stato: Accettata
Data: 2026-11-06

## Contesto
- Alessio ha chiesto il massimo della configurazione di GitHub, per questo e per i prossimi progetti, restando su servizi gratuiti (ADR 0010).
- Fatti dalla documentazione e dalla pagina prezzi di GitHub, letti il 2026-11-06, piano Free di un account personale:
  - su un repository **privato** non ci sono protezione dei branch né ruleset (l'API risponde "Upgrade to GitHub Pro or make this repository public to enable this feature"), né CodeQL, secret scanning, push protection e dependency review (servono GitHub Code Security o Secret Protection, a pagamento); restano Dependabot e circa 2.000 minuti di Actions al mese;
  - su un repository **pubblico** sono gratuiti: ruleset, CodeQL, secret scanning con push protection, dependency review, segnalazione privata delle vulnerabilità, e i minuti delle Actions sui runner standard.
- Il codice non contiene segreti: le chiavi stanno nei secrets di GitHub e nei file `.env` ignorati da Git.

## Opzioni
1. Restare privato, con la configurazione possibile sul piano Free.
2. Passare a GitHub Pro (a pagamento): protezione dei branch sul privato, ma non gli strumenti di sicurezza.
3. **Rendere pubblico il repository**, dopo un controllo di sicurezza.

## Decisione
Deciso da Alessio il 2026-11-06: **opzione 3**, pubblico dopo un controllo di sicurezza.

Controllo fatto da Claude prima del cambio (nessun risultato positivo):
- cronologia completa (59 commit, tutti i branch): nessun file `.env` o simile mai aggiunto; nessuna occorrenza dei valori reali (chiave Hevy, password del database locale e del superutente, password e host di Neon, token SonarQube); nessuno schema tipico di chiave o password;
- messaggi di commit, testi e commenti di issue e PR, log dei giri `Hevy sync` e di CI: nessuna occorrenza.

Configurazione applicata il 2026-11-06:
- impostazioni: cancellazione del branch dopo il merge, auto-merge permesso, pulsante "Update branch";
- sicurezza: secret scanning con push protection, aggiornamenti di sicurezza di Dependabot (gli avvisi erano già attivi), segnalazione privata delle vulnerabilità, CodeQL in "default setup" con la suite estesa;
- ruleset `protect-main` sul branch principale, senza eccezioni: niente cancellazione, niente force push, modifiche solo tramite PR (0 approvazioni: GitHub non permette di approvare le proprie PR), conversazioni risolte, job `build` della CI verde. Non è richiesto che il branch sia aggiornato con `main` prima del merge, per non obbligare a un aggiornamento a ogni PR;
- file: template di PR (con la checklist delle linee guida) e di issue, `CODEOWNERS`, `SECURITY.md`, workflow `Dependency review` sulle PR (fallisce con vulnerabilità di gravità alta o superiore).

## Conseguenze
- Codice, cronologia, issue, PR e log delle Actions sono pubblici: niente segreti né dati personali in nessuno di questi posti, mai (già regola di `CLAUDE.md`). I secrets di GitHub restano privati e nei log vengono mascherati.
- La regola "mai commit su `main`" ora la fa rispettare GitHub.
- I minuti delle Actions sui runner standard non hanno più un limite: si può rivalutare PIT a ogni PR (ADR 0011).
- Per i prossimi progetti: un repository template e uno script che applica questa configurazione (un template copia i file, non le impostazioni).
