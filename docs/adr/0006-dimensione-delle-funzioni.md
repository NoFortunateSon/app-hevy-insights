# 0006 - Dimensione delle funzioni: piccole ovunque o moduli profondi

Stato: Accettata
Data: 2026-11-06

## Contesto
- Due libri di Alessio danno indicazioni in tensione. Fowler (*Refactoring* 2ed, cap. 3) vuole funzioni molto piccole, nominate per intenzione; ogni commento che spiega cosa fa un blocco è un segnale per estrarlo. Ousterhout (*A Philosophy of Software Design* 2ed, cap. 9 e principio 4) vuole moduli profondi e avverte che spezzare troppo produce pezzi che si capiscono solo insieme (la sezione 9.8 discute la differenza con un altro approccio).
- Il progetto ha un modulo per responsabilità (`hevyclient`, `sync`, `metrics`, `api`) e Claude Code scrive la maggior parte del codice: senza una regola scelta, ogni sessione sceglie a suo gusto.
- Fatti: nessuno dei due libri dà un numero di righe; entrambi dicono di fidarsi del giudizio.

## Opzioni
1. **Fowler come default**: estrarre funzioni appena un blocco ha un nome naturale; nessun commento che spiega cosa fa.
   - Pro: funzioni brevi, nomi che documentano.
   - Contro: rischio di tante funzioni piccole e dipendenti tra loro.
2. **Ousterhout come default**: classi e moduli profondi; funzione lunga accettata se fa una cosa sola e si legge dall'alto in basso; estrarre solo se il pezzo ha uno scopo proprio e un nome preciso.
   - Pro: meno frammentazione.
   - Contro: funzioni che crescono senza limite se nessuno le guarda.
3. **Regola ibrida**: Ousterhout per l'interfaccia dei moduli e delle classi pubbliche, Fowler dentro la classe per i blocchi con nome naturale. Nessun limite di righe; in revisione si guarda se la funzione fa una cosa e se i pezzi estratti si capiscono da soli.
   - Pro: usa il punto forte di ciascuno. Contro: richiede giudizio, quindi meno verificabile.

## Decisione
Deciso da Alessio il 2026-11-06: **opzione 3, regola ibrida**.
- Interfaccia di moduli e classi pubbliche: piccola e profonda (Ousterhout). Nessun metodo pubblico in più per spezzare il lavoro.
- Dentro la classe: si estrae un metodo privato quando il blocco ha un nome naturale e si capisce da solo (Fowler). Non si estrae un pezzo che si capisce solo leggendo chi lo chiama.
- Nessun limite di righe o di parametri numerico. In revisione si guarda se la funzione fa una cosa sola e se i pezzi estratti si leggono da soli.

## Conseguenze
- Cambia la regola dedicata in `docs/linee-guida.md` e va aggiornato `CLAUDE.md` solo se la scelta aggiunge un obbligo.
- Eventuali limiti numerici (righe, parametri) sarebbero una scelta nostra, non dei libri: da dichiarare come tale.
