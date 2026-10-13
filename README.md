# Runtime Object to JSON

An IntelliJ IDEA plugin that exports the runtime value of a Java variable to JSON while the application is paused in the debugger.

# Features

Reads a Java variable from the debugger, retrieves the actual values stored in the JVM, and recursively traverses nested objects to generate a formatted JSON file, which is saved in the project's JSON folder.

# USage
1) Place a breakpoint after the object has been initialized.
2) Start the application in Debug mode.
3) Place the cursor over a Java variable or select it.
4) Right-click in the editor.
5) Select Export Object to JSON.


# Build
Compile the plugin:

.\gradlew.bat clean build
Launch the IDE Sandbox:

.\gradlew.bat runIde
Create a distributable ZIP:

.\gradlew.bat buildPlugin
Output:

build/
└── distributions/
    └── PluginJson-1.0.0.zip
    
# Compatibility Verification

Verify project configuration:
.\gradlew.bat verifyPluginProjectConfiguration

Verify project structure:
.\gradlew.bat verifyPluginStructure

Plugin Verifier:
.\gradlew.bat verifyPlugin

Result:
# Compatibility
IntelliJ IDEA 2025.1+
Java 21 to compile the plugin
