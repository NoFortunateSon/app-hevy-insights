# 0010 - Ambiente di esecuzione gratuito, attivo a PC spento

Stato: Accettata
Data: 2026-11-06

## Contesto
- Alessio vuole che l'app giri anche a PC spento, **solo con servizi gratuiti**.
- Oggi l'app gira solo in locale; il database è il Postgres condiviso di `platform-infra` (ADR 0003, "solo locale" tra i punti aperti di `CLAUDE.md`).
- Cosa deve girare oggi: la sync con Hevy ogni 6 ore (ADR 0008). Domani: le API REST in sola lettura sulle metriche, che servono solo quando qualcuno le chiama.
- Le variabili (`HEVY_API_KEY`, credenziali del database) vanno impostate nell'ambiente; l'app le legge già dalle variabili d'ambiente, che vincono su `.env` (issue #36).

Fatti dalle pagine ufficiali, lette il 2026-11-06:
- **Oracle Cloud, Always Free** (`docs.oracle.com`, pagina "Always Free Resources"): risorse gratuite "for the life of the account"; VM Ampere A1 fino a 2 OCPU e 12 GB di RAM in tutto; 200 GB di disco; 10 TB/mese di traffico in uscita. Nessun Postgres gestito gratuito (solo Autonomous Database e MySQL). **"Idle Always Free compute instances may be reclaimed by Oracle"** se in 7 giorni CPU (95° percentile), rete e memoria restano tutte sotto il 20%. La pagina commerciale (`oracle.com/cloud/free`) ha risposto 403: se serva una carta di credito **non è verificato**.
- **Google Cloud, Free Tier** (`docs.cloud.google.com/free`): una VM `e2-micro` al mese in tre regioni USA, 30 GB di disco, 1 GB/mese di traffico in uscita; "no end date", modificabile con 30 giorni di preavviso; serve un account di fatturazione (metodo di pagamento). La RAM della `e2-micro` non è indicata in quella pagina.
- **GitHub Actions** (`docs.github.com`): per i repository privati il piano Free include 12 USD al mese di calcolo; un runner Linux standard costa 0,006 USD al minuto (conto mio: circa 2.000 minuti al mese). Un job dura al massimo 6 ore. I workflow pianificati (`schedule`) hanno un intervallo minimo di 5 minuti e "can be delayed during periods of high loads"; con carico molto alto alcuni possono essere saltati. La disattivazione dopo 60 giorni senza attività è documentata per i repository pubblici (non letta testualmente per i privati).
- **Neon** (`neon.com/pricing`, aggiornata il 2026-11-04): Postgres gestito, "The Free plan is permanent (not a trial); no credit card required"; 1 GB per progetto; si spegne dopo 5 minuti senza uso e riparte alla prima connessione; "Neon doesn't pause Free projects for inactivity".
- Scartati, con i fatti: **Render** spegne il servizio gratuito dopo 15 minuti senza traffico e cancella il Postgres gratuito 30 giorni dopo la creazione; **Fly.io** "New organizations don't have a free tier"; **Koyeb** non mostra calcolo gratuito; **Supabase** mette in pausa il Postgres gratuito dopo 1 settimana di inattività.

## Opzioni
1. **VM Oracle Always Free** con app e Postgres in Docker Compose (come `platform-infra`).
   - Pro: tutto acceso sempre (sync e futura API REST); risorse abbondanti; stesso Postgres 18 dei test.
   - Contro: un'app che lavora 4 volte al giorno resta quasi sempre sotto il 20% → **rischio concreto che Oracle recuperi la VM** (ipotesi basata sulla regola citata); si mantiene un server (aggiornamenti, sicurezza, backup fuori dalla VM); carta di credito probabilmente richiesta (non verificato).
2. **GitHub Actions pianificato + Postgres su Neon.** Ogni 6 ore un workflow avvia l'app in modalità "una sync e poi esci", che scrive su Neon.
   - Pro: nessun server da mantenere; nessuna carta (Neon dichiarato senza carta; GitHub già in uso); i segreti stanno nei "secrets" del repository. Stima, non misurata: con 4 giri al giorno di pochi minuti l'uno si resta lontani dai ~2.000 minuti gratuiti.
   - Contro: nessun processo sempre acceso, quindi **niente API REST** finché non si aggiunge altro; gli orari possono slittare o un giro può saltare (il successivo recupera, grazie al punto di ripartenza); serve una piccola modifica al codice (modalità "una sync e poi esci") e un database diverso da quello locale.
3. **VM Google `e2-micro`** con app e Postgres.
   - Pro: nessun recupero per inattività dichiarato.
   - Contro: risorse molto ridotte per Java più Postgres (RAM non verificata sulla pagina); 1 GB/mese di traffico; serve un account di fatturazione.

Raccomandazione di Claude: **2 adesso**, perché copre l'unica cosa che oggi deve girare a PC spento (la sync) senza server da curare e senza carta. Quando arriveranno le API REST si rivaluta: Neon resta il database e si aggiunge solo dove far girare l'API (per esempio l'opzione 1).

## Decisione
Deciso da Alessio il 2026-11-06: **opzione 2**, GitHub Actions pianificato + Postgres su Neon (piano gratuito). Le API REST, quando arriveranno, avranno un ADR loro su dove girare.

## Conseguenze
- Nuovo workflow GitHub Actions pianificato ogni 6 ore (a un minuto non tondo, come chiede Hevy), più l'avvio manuale.
- Il codice ottiene una modalità "una sync e poi esci" (proprietà di configurazione), oltre alla sync periodica che resta per l'uso locale.
- Database: un progetto Neon gratuito; l'URL e le credenziali diventano segreti del repository GitHub, come `HEVY_API_KEY`. Le migrazioni Flyway si applicano da sole al primo avvio.
- Il Postgres locale di `platform-infra` resta per lo sviluppo; i dati locali e quelli su Neon sono separati.
- `CLAUDE.md` (punto aperto "solo locale") e `docs/STATO.md` vanno aggiornati.
- Da verificare alla prima esecuzione: durata reale di un giro (minuti consumati), connessione a Neon con SSL, versione di Postgres offerta da Neon rispetto alla 18 dei test.
