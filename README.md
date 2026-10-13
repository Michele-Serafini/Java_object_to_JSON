Runtime Object to JSON
Plugin IntelliJ IDEA che permette di esportare in formato JSON il valore runtime di una variabile Java mentre l'applicazione è ferma nel debugger.

Funzionalità
Il plugin:

legge una variabile Java dal debugger;
recupera i valori reali presenti nella JVM;
attraversa ricorsivamente gli oggetti annidati;
gestisce tipi primitivi, wrapper, enum e array;
evita cicli infiniti tra oggetti collegati;
genera un file JSON formattato;
salva il file nella cartella JSON del progetto.
Esempio
Codice Java
Customer customer = new Customer();

customer.setId(123L);
customer.setName("Michele");

Country country = new Country();
country.setCode("IT");

Address address = new Address();
address.setCity("Verona");
address.setCountry(country);

customer.setAddress(address);
JSON generato
{
  "id": 123,
  "name": "Michele",
  "address": {
    "city": "Verona",
    "country": {
      "code": "IT"
    }
  }
}
Utilizzo
Avvia l'applicazione in modalità Debug.
Posiziona un breakpoint dopo che l'oggetto è stato valorizzato.
Attendi che il debugger si fermi.
Posiziona il cursore sopra una variabile Java oppure selezionala.
Click destro nell'editor.
Seleziona:
Export Object to JSON
Il file viene creato automaticamente nella cartella:
JSON/
Esempio:

JSON/
└── customer_2026-10-12_17-35-20-123.json
Architettura
ExportRuntimeFromEditorAction
        ↓
RuntimeScannerService
        ↓
JsonExporterService
        ↓
file JSON
Classi principali
ExportRuntimeFromEditorAction
Classe che integra il plugin nell'interfaccia di IntelliJ.

Responsabilità:

recupera il progetto e l'editor correnti;
individua la variabile selezionata;
accede alla sessione di debug;
recupera lo stack frame corrente;
legge il valore runtime della variabile;
avvia la generazione del JSON.
Flusso:

utente
↓
click destro
↓
Export Object to JSON
↓
Debugger
↓
RuntimeScannerService
RuntimeScannerService
Classe responsabile della conversione degli oggetti runtime in JSON.

Responsabilità:

legge i valori presenti nella JVM;
attraversa ricorsivamente gli oggetti;
gestisce enum, wrapper e array;
evita riferimenti circolari;
applica limiti di sicurezza.
Esempio:

Customer
├─ id
├─ name
└─ address
   └─ country
↓

{
  "id": 123,
  "name": "Michele",
  "address": {
    "country": {
      "code": "IT"
    }
  }
}
JsonExporterService
Classe responsabile del salvataggio del file.

Responsabilità:

converte il JsonElement in testo JSON;
crea la cartella JSON;
genera il nome del file;
scrive il file in UTF-8;
aggiorna automaticamente IntelliJ;
notifica l'utente al termine.
Thread utilizzati
Il plugin utilizza thread differenti per evitare blocchi dell'interfaccia.

UI Thread
Gestisce:

menu;
popup;
editor;
interfaccia grafica.
Debugger Manager Thread
Gestisce:

lettura delle variabili runtime;
accesso agli ObjectReference;
comunicazione con la JVM in debug.
Background Thread
Gestisce:

serializzazione JSON;
scrittura dei file sul disco.
Gestione degli Enum
Esempio:

public enum Status {
    ACTIVE,
    DISABLED
}
Valore:

Status.ACTIVE
JSON:

"ACTIVE"
Gestione dei Wrapper
Esempio:

Long id = 123L;
JSON:

123
e non:

{
  "value": 123
}
Wrapper supportati:

Boolean
Byte
Short
Integer
Long
Float
Double
Character
String
Gestione delle ricorsioni
Oggetti come:

Customer
 ↓
Address
 ↓
Customer
creerebbero un loop infinito.

Per evitarlo il plugin tiene traccia degli oggetti già visitati.

Risultato:

{
  "$ref": "com.example.Customer#843"
}
Limiti di sicurezza
Per evitare esportazioni troppo grandi:

MAX_DEPTH
MAX_ARRAY_ITEMS
MAX_OBJECTS
Questi limiti impediscono:

ricorsioni infinite;
array enormi;
grafi di oggetti troppo grandi.
Quando viene raggiunto un limite:

{
  "$truncated": "Maximum depth reached"
}
Struttura del progetto
src/
└── main/
    ├── java/
    │   └── pluginjson/
    │       ├── ExportRuntimeFromEditorAction.java
    │       └── scanner/
    │           ├── RuntimeScannerService.java
    │           └── JsonExporterService.java
    │
    └── resources/
        └── META-INF/
            ├── plugin.xml
            └── pluginIcon.svg
Build
Compilazione:

.\gradlew.bat clean build
Avvio IDE sandbox:

.\gradlew.bat runIde
Creazione ZIP distribuibile:

.\gradlew.bat buildPlugin
Output:

build/
└── distributions/
    └── PluginJson-1.0.0.zip
Verifica compatibilità
Configurazione progetto:

.\gradlew.bat verifyPluginProjectConfiguration
Verifica struttura:

.\gradlew.bat verifyPluginStructure
Plugin Verifier:

.\gradlew.bat verifyPlugin
Risultato attuale:

Compatible
Plugin can probably be enabled or disabled without IDE restart
Requisiti
IntelliJ IDEA 2025.1+
Java 21 per compilare il plugin
Debug Java attivo
Applicazione ferma su un breakpoint
Autore
Michele Serafini
