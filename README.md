# Runtime Object to JSON

Plugin IntelliJ IDEA che permette di esportare in formato JSON il valore runtime di una variabile Java mentre l'applicazione è ferma nel debugger.

# Funzionalità

Legge una variabile Java dal debugger, recupera i valori reali presenti nella JVM e attraversa ricorsivamente gli oggetti annidati per generare un file JSON formattato, salvato nella cartella JSON del progetto.

# Utilizzo
1) Posiziona un breakpoint dopo che l'oggetto è stato valorizzato.
2) Avvia l'applicazione in modalità Debug.
3) Posiziona il cursore sopra una variabile Java oppure selezionala.
4) Click destro nell'editor.
5) Seleziona: Export Object to JSON


# Build
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
    
# Verifica compatibilità

Configurazione progetto:
.\gradlew.bat verifyPluginProjectConfiguration

Verifica struttura:
.\gradlew.bat verifyPluginStructure

Plugin Verifier:
.\gradlew.bat verifyPlugin
Risultato attuale:

# Compatibilità
IntelliJ IDEA 2025.1+
Java 21 per compilare il plugin
