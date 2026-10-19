# SpesaRadar 0.1

App Android personale: osservi un prodotto o una marca e l'app trova le offerte
nei volantini dei negozi di Bologna (PromoQui), su Amazon.it e negli altri negozi
online (Trovaprezzi). Le ordina per prezzo al litro / al chilo / al pezzo, salva lo
storico giorno per giorno e manda una notifica per minimo storico, prezzo sotto
soglia o nuove offerte.

## Compilazione

Come per l'app Libero: carica tutta la cartella in un nuovo repo GitHub
(`nikorma/spesaradar`), compresa la cartella `.github`. GitHub Actions compila
da solo; l'APK si scarica da Actions → ultima esecuzione → Artifacts → `SpesaRadar-apk`.

La chiave di firma è fissa (`app/debug.keystore`): le versioni successive si
installano sopra la vecchia senza perdere lo storico prezzi. Non cancellarla.

## Come funziona

- Le pagine vengono aperte in una WebView invisibile sul telefono, come un normale
  browser: per questo PromoQui e Amazon non bloccano la lettura.
- `app/src/main/assets/extract.js` legge le offerte dalla pagina.
- Dati in SQLite sul telefono: `offer` (offerte attive), `history` (un prezzo al giorno
  per offerta), `fetch_log` (diagnostica).
- Controllo automatico ogni giorno all'ora scelta (WorkManager), più il tasto Aggiorna.

## Taratura delle fonti

Impostazioni → Diagnostica delle fonti mostra quante offerte ha letto ogni sito.
"Condividi pagina" esporta l'HTML letto: serve a correggere `extract.js` se un sito
non viene letto bene.

© 2026 nikorma — Orma Studio
