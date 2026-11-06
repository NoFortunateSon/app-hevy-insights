# 0009 - Tecnologia del repository: JPA

Stato: Accettata
Data: 2026-11-06

## Contesto
- L'ADR 0007 mette l'accesso al database nel package `repository` e le righe delle tabelle nel package `model`. Oggi il `repository` non ha classi: la prima arriva con la sync (ADR 0008).
- Lo schema lo gestisce Flyway (ADR 0004). La tabella `hevy_workout_raw` (ADR 0005) ha una chiave composta (`user_id`, `hevy_workout_id`) e una colonna `payload` di tipo `JSONB`.
- Serve scegliere come il codice Java legge e scrive le tabelle prima di scrivere la sync.

Fatti verificati il 2026-11-06 sul BOM di Spring Boot 4.1.2 (`spring-boot-dependencies-4.1.2.pom` nel repository Maven locale) e su Maven Central:
- `spring-boot-starter-data-jpa` e `spring-boot-starter-data-jpa-test` sono gestiti dal BOM e pubblicati alla 4.1.2 (Maven Central risponde 200).
- Versioni dal BOM: Hibernate `7.4.13.Final`, Jakarta Persistence `3.2.0`, Spring Data `2026.0.2`.

## Opzioni
1. **JDBC con `JdbcClient`** (già sul classpath, lo usano i test dello schema). Pro: nessuna dipendenza nuova, SQL esplicito. Contro: mappatura righe-oggetti scritta a mano.
2. **Spring Data JDBC.** Pro: repository dichiarativi, modello semplice senza sessione. Contro: dipendenza nuova; meno diffuso.
3. **JPA (Spring Data JPA con Hibernate).** Pro: lo standard più diffuso in Spring; repository dichiarativi; entità con relazioni quando arriveranno le tabelle normalizzate. Contro: dipendenza nuova e più pesante; contesto di persistenza da capire (caricamenti pigri, flush).

## Decisione
Deciso da Alessio il 2026-11-06: **opzione 3, JPA**.

## Conseguenze
- Dipendenze `spring-boot-starter-data-jpa` (e `spring-boot-starter-data-jpa-test` per i test), senza versione: la gestisce il BOM di Spring Boot. Si aggiungono al `pom.xml` nella PR della sync, insieme alla prima entità, non prima (regola 13: niente dipendenze non usate). Esito (issue #34): aggiunto solo `spring-boot-starter-data-jpa`; lo starter di test non serve finché i test usano `@SpringBootTest` con Testcontainers.
- Lo schema resta di Flyway: Hibernate non crea né modifica tabelle (`spring.jpa.hibernate.ddl-auto=validate`, controlla solo che le entità corrispondano allo schema). Ogni cambio di schema resta una migrazione nuova (ADR 0004).
- Proposta di Claude per la configurazione, da confermare nella PR della sync: `spring.jpa.open-in-view=false`, così le query partono solo da service e repository e non durante la scrittura della risposta HTTP.
- Dove va il codice (ADR 0007): le entità JPA sono **model** (`model/`, righe delle nostre tabelle); le interfacce Spring Data JPA stanno in `repository/`; i service le usano, i controller no (`LayerDependencyTests`).
- Regola 8 delle linee guida (preferire `record` e oggetti immutabili): le entità JPA richiedono un costruttore vuoto e campi modificabili. Lo prevede già la regola 8 ("Entità JPA... confinale in un solo punto"): restano in `model`, i DTO restano `record`.
- Verificato nella PR della sync (issue #34) con Testcontainers: l'app parte con `ddl-auto=validate` sullo schema di Flyway e i test di integrazione leggono e scrivono le tabelle. Erano da verificare:
  - mappatura della colonna `JSONB` su un campo testo dell'entità;
  - chiave composta (`user_id`, `hevy_workout_id`);
  - `ddl-auto=validate` che passa sullo schema creato da Flyway.
- Prestazioni: per un solo utente e qualche centinaio di workout non si prevedono problemi (ipotesi, non misurato).
