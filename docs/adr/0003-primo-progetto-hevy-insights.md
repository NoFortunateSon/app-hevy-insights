# 0003 - Primo progetto: analisi dei dati Hevy

Stato: Accettata (ambito, opzione A, nome del repo e `groupId`). Resta aperto il modo dell'integrazione con Claude (vedi "Punti aperti").
Data: 2026-11-06

## Contesto
- Serve un primo progetto piccolo e vero per provare tutta la catena: repo, ADR, test, CI, branch + PR, compose, Maven.
- Alessio usa Hevy. Non vuole copiare l'app (log delle serie, timer): gli serve la parte che Hevy non fa, cioe' analisi e uso dei dati con Claude.
- Fatti verificati il 6/11/2026: l'API pubblica di Hevy richiede l'abbonamento Pro e una chiave personale; Hevy dichiara di non garantirne la stabilita'; la chiave equivale a una password. Esiste un connettore Hevy in sessione (lettura e scrittura, nessuna cancellazione).
- Uso iniziale: solo Alessio. Si vuole non chiudere la porta a un uso con altri utenti.

## Opzioni
1. **A. Servizio di analisi** (Java 25, Spring Boot 4.1, Maven, Postgres): sincronizza i dati da Hevy, calcola metriche, le espone. Pro: usa lo stack scelto, una sola app, nessuna scrittura su Hevy. Contro: l'integrazione con Claude va decisa a parte.
2. **B. Server MCP in Java o Node**: tool di alto livello sopra l'API. Pro: riusabile. Contro: esiste gia' `hevy-mcp` e il connettore; il valore e' solo nelle analisi, che comunque vanno calcolate da qualche parte.
3. **C. Coach settimanale** che scrive le routine su Hevy. Pro: la piu' utile. Contro: scrive su dati reali con creazioni non idempotenti; richiede prima l'analisi (A).
4. Non fare nulla e usare solo il connettore in chat: costo zero, ma nessun dato storico calcolato e nessuna prova della catena di lavoro.

## Decisione
Opzione A, in sola lettura verso Hevy. B e C restano possibili dopo, come evoluzioni di A.

Deciso da Alessio il 6/11/2026:
- Ambito della prima versione: **carichi, volume, frequenza, recuperi, misure corporee**.
- Repo: `NoFortunateSon/app-hevy-insights`, privato (cartella locale `C:\dev\apps\app-hevy-insights`).
- `groupId` Maven: `io.github.nofortunateson`.

## Impostazione proposta (da rivedere nel primo ciclo di lavoro)
> Struttura dei package sostituita dall'ADR 0007 (tre livelli) il 2026-11-06. Il resto di questo ADR resta valido.

Monolite modulare, moduli come package separati con dipendenze in una sola direzione:

| Modulo | Compito |
|---|---|
| `hevy-client` | Chiamate all'API Hevy, paginazione, gestione errore 429; nessuna logica di dominio |
| `sync` | Sincronizzazione incrementale (primo carico, poi solo le modifiche); salva il JSON originale e le righe normalizzate |
| `metrics` | Calcolo delle metriche sui dati gia' salvati; nessuna chiamata a Hevy |
| `api` | Interfaccia REST in lettura sulle metriche |

Metriche iniziali (definizioni **proposte**, da confermare con Alessio sui suoi dati veri):

| Area | Definizione proposta |
|---|---|
| Carichi | Serie migliore per esercizio e stima del massimale con la formula di Epley (peso x (1 + ripetizioni/30)), usata solo come indicatore |
| Volume | Somma di peso x ripetizioni per gruppo muscolare e per settimana; in alternativa numero di serie |
| Frequenza | Sessioni a settimana per gruppo muscolare |
| Recuperi | Ore tra due sessioni che allenano lo stesso gruppo. **Ipotesi:** non so se l'API espone i tempi di recupero tra le serie; va controllato sui dati reali prima di promettere questa metrica |
| Misure corporee | Serie storica delle misure e variazione sul periodo |

Regole di sicurezza e di apertura futura:
- La chiave Hevy non entra mai nel repo ne' nei log: variabile d'ambiente da gestore di password, `.env` fuori da Git, solo `.env.example`.
- Ogni tabella ha una colonna `user_id` dal primo giorno (costo basso). Autenticazione, chiave Hevy cifrata per utente e multi-utente restano fuori dalla prima versione e avranno un ADR proprio.
- Test: contratto sul client Hevy con risposte registrate; test sul calcolo delle metriche con dati inventati; nessun test che chiami l'API vera in CI.
- Hevy puo' cambiare l'API: il client e' isolato in un solo modulo per questo motivo.
- Repo privato su piano GitHub Free: `main` non e' protetto da GitHub. Il lavoro avviene solo su branch `claude/<issue>-<tema>` con PR; la CI deve essere verde.

## Punti aperti (nessuno decide Claude)
1. **Integrazione con Claude**: (a) la sola chat con il connettore Hevy, senza codice; (b) l'app espone le metriche come server MCP, cosi' Claude le interroga; (c) l'app chiama l'API di Anthropic per generare un report (costo a parte, serve una chiave). Decisione rimandata a un ADR dopo la prima versione delle metriche.
2. Dove gira: solo locale (compose) o anche in Kubernetes. Per ora solo locale.
3. Spring Modulith: verificato online il 6/11/2026 che la linea 2.1 e' allineata a Spring Boot 4.1 (la 2.1 M1 sale a Boot 4.1 M1, la 2.1.1 e' uscita il 26/8/2026; la 2.2 M1 passa a Boot 4.2 M1). **Non ho trovato una dichiarazione esplicita di compatibilita' per la 2.1 GA**: la versione esatta si fissa nella prima PR guardando la pagina del progetto su spring.io e la build deve passare. Per i moduli basta una struttura a package, Modulith e' facoltativo.
4. Spring AI MCP (serve solo se si sceglie 1b): verificato il 6/11/2026 che Spring AI 2.0.0 GA e' uscito il 12/6/2026 per Spring Boot 4.0 / 4.1, con `@McpTool`, `@McpResource`, `@McpPrompt` e starter MCP per server WebMVC/WebFlux/STDIO. Fonte: blog Spring. Da riverificare quando si arriva a quella decisione.

## Conseguenze
- Il primo giro completo (repo, CI, compose, DB) si fa su un caso reale e piccolo.
- Il valore per Alessio arriva dalle metriche; l'integrazione con Claude resta una scelta successiva e informata.
- Tornare indietro costa poco: nessuna scrittura su Hevy, dati copiati in un DB locale.
- Rischio: se Hevy chiude o cambia l'API, il client va riscritto; i dati gia' salvati restano.
