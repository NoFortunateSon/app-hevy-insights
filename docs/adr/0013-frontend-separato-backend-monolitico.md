# 0013 - Frontend separato, backend monolitico

Stato: Accettata
Data: 2026-11-06

## Contesto
- Alessio ha valutato di dividere il progetto in microservizi, con gateway, frontend e il resto.
- Fatti: il backend ha circa 1.000 righe, un solo utente, una sync ogni 6 ore; per stare gratis non c'è un posto sempre acceso dove far girare più servizi (ADR 0010).
- Opinione di Claude: i microservizi portano rete tra servizi, più deploy, dati distribuiti, contratti e tracciamento, in cambio di scalabilità e team indipendenti che qui non servono; un gateway davanti a un solo backend non aggiunge nulla. Un primo processo separato esiste già: la sync gira da sola su GitHub Actions.

## Opzioni
1. Microservizi con gateway e frontend.
2. **Frontend separato, backend monolitico** con le API REST in sola lettura (ADR 0003, 0007).
3. Tutto in un'unica applicazione, frontend compreso.

## Decisione
Deciso da Alessio il 2026-11-06: **opzione 2**, per ora.

## Conseguenze
- Il backend resta questo repository con la struttura dell'ADR 0007; niente gateway.
- Il frontend sarà un'applicazione a parte che chiama le API REST. Tecnologia, repository e hosting gratuito: ADR futuri, da decidere quando arrivano le API delle metriche.
- Se emergerà un confine vero (per esempio la sync con esigenze proprie), lo si estrae allora, con un ADR.
