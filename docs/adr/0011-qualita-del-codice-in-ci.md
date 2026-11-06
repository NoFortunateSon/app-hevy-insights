# 0011 - Qualità del codice in CI: SonarQube e strumenti che mettono alla prova il codice

Stato: Accettata
Data: 2026-11-06

## Contesto
- Alessio chiede, prima di andare avanti con le metriche, SonarQube nella pipeline e qualche plugin che metta alla prova il codice.
- Oggi la CI (`.github/workflows/ci.yml`) esegue `mvn verify`: compila e lancia i test (47, con Testcontainers). Non misura la copertura, non fa analisi statica, non verifica se i test si accorgono degli errori.
- Vincoli: solo servizi gratuiti (ADR 0010); repository privato; Java 25 (ADR 0001/0002 del workspace); ogni nuova dipendenza o plugin passa da un ADR (`CLAUDE.md`).

Fatti verificati il 2026-11-06 su pagine ufficiali:
- **SonarQube Cloud** (`sonarsource.com/plans-and-pricing/sonarcloud`): piano gratuito "free for your private projects up to 50k LOC. No card, no expiry." Si contano le righe del branch più grande. L'analizzatore Java: "LTS 8, 11, 17, 21, 25 and all intermediary versions are fully supported." Analisi da CI con Maven: `mvn verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar` con il secret `SONAR_TOKEN` e le proprietà `sonar.organization` e `sonar.projectKey`; l'analisi automatica di SonarQube Cloud va spenta ("Automatic analysis is not intended to be used in conjunction with CI-based analysis"). La copertura non la calcola Sonar: legge il rapporto XML di JaCoCo (`target/site/jacoco/jacoco.xml`). Utenti e limiti su branch e PR del piano gratuito: non indicati.
- **SonarQube Community Build** (self-hosted): gratuito e open source, ma è un server da far girare (Docker/Kubernetes): servirebbe un posto sempre acceso, che oggi non abbiamo gratis (ADR 0010).
- Versioni più recenti su Maven Central e supporto Java 25 dai changelog ufficiali:

| Strumento | A cosa serve | Ultima versione | Java 25 |
|---|---|---|---|
| JaCoCo (`jacoco-maven-plugin`) | Copertura dei test (quali righe i test eseguono) | 0.8.16 | Sì: "JaCoCo now officially supports Java 25" (0.8.14) |
| PIT (`pitest-maven` + `pitest-junit5-plugin`) | **Mutation testing**: modifica il codice di proposito (per esempio cambia un `<` in `<=`) e controlla che almeno un test fallisca. Misura se i test *mettono davvero alla prova* il codice, non solo se lo eseguono | 1.30.2 + 1.2.3 | Non dichiarato; c'è una correzione per Java 25 nella 1.25.8. Da provare |
| PMD (`maven-pmd-plugin` + `pmd-java`) | Regole statiche (codice morto, complessità, errori comuni) | 3.28.0 + 7.29.0 | Sì: "Supported by PMD since 7.16.0" |
| SpotBugs (`spotbugs-maven-plugin`) | Bug probabili sul bytecode (null, risorse non chiuse) | 4.10.5.0 | Gira su Java 25; analisi di classi Java 25 non dichiarata |
| Checkstyle | Stile e formattazione | 14.4.0 | Sì, indirettamente (correzioni per sintassi Java 25) |
| Error Prone | Errori comuni al momento della compilazione | 2.52.0 | Non dichiarato |

## Opzioni

### A. Dove gira SonarQube
1. **SonarQube Cloud, piano gratuito**, analisi dalla CI a ogni PR e a ogni push su `main`.
   - Pro: gratis per un repository privato fino a 50k righe (siamo molto sotto); nessun server; storico e "quality gate" sul codice nuovo; Java 25 supportato.
   - Contro: serve un account SonarQube Cloud collegato a GitHub e il secret `SONAR_TOKEN` (li crea Alessio); il codice viene analizzato su un servizio esterno.
2. **SonarQube Community Build in un container dentro ogni giro di CI**.
   - Pro: nessun account esterno. Contro: nessuno storico tra un giro e l'altro (il server sparisce), giri più lenti; si perde gran parte del valore.
3. **Niente SonarQube**, solo plugin Maven.

### B. Strumenti che mettono alla prova il codice
1. **JaCoCo + PIT** (copertura per Sonar + mutation testing). Le regole statiche le fa già SonarQube.
2. **JaCoCo + PIT + PMD + SpotBugs + Checkstyle**: tutto.
   - Contro: buona parte delle regole di PMD, SpotBugs e Checkstyle si sovrappone a quelle di SonarQube; più tempo di CI e più falsi allarmi da gestire in due posti.
3. **Solo JaCoCo.**

### C. Quando bloccare una PR
1. **Si parte senza soglie bloccanti**: i rapporti si leggono, e dopo il primo giro si fissano le soglie sul valore misurato (per esempio "il punteggio di PIT non scende"), più il quality gate di Sonar sul codice nuovo.
2. **Soglie bloccanti subito** (per esempio copertura 80%, mutazioni uccise 70%): numeri scelti a tavolino, prima di sapere da dove si parte.

Raccomandazione di Claude: **A1, B1, C1**. SonarQube Cloud è l'unico modo gratuito di avere SonarQube con lo storico senza un server; PIT è lo strumento che più "mette alla prova" il codice; i plugin statici in più duplicherebbero SonarQube. Le soglie si fissano sui numeri veri.

## Decisione
Deciso da Alessio il 2026-11-06: **le raccomandazioni**. A1 SonarQube Cloud (piano gratuito), B1 JaCoCo + PIT, C1 nessuna soglia bloccante finché non si misura il punto di partenza.

## Conseguenze
- `pom.xml`: plugin JaCoCo (rapporto XML in `verify`) e PIT (in un profilo, perché il mutation testing è lento e non serve in ogni build locale); versioni fissate a quelle verificate sopra.
- `ci.yml`: un passo che, se `SONAR_TOKEN` esiste, lancia l'analisi SonarQube dopo `mvn verify`; un passo per PIT con il rapporto salvato come artefatto del giro. Le PR di Dependabot non ricevono i secrets: per loro l'analisi Sonar si salta.
- Alessio: crea l'organizzazione e il progetto su SonarQube Cloud importando il repository GitHub, spegne l'analisi automatica, crea un token e lo salva come secret `SONAR_TOKEN`. Le chiavi `sonar.organization` e `sonar.projectKey` vanno nel `pom.xml` (non sono segrete).
- Da verificare nella PR: che PIT funzioni con Java 25 e Testcontainers (se non funziona, si scrive e si decide); quanto tempo aggiunge alla CI.
