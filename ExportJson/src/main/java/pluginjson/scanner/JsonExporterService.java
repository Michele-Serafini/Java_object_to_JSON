package pluginjson.scanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.jetbrains.annotations.NotNull;

public class JsonExporterService {

  private static final Logger LOG =
      Logger.getInstance(JsonExporterService.class);

  private static final String DIALOG_TITLE =
      "Export Object to JSON";

  private static final String OUTPUT_DIRECTORY =
      "JSON";

  /*
   * I millisecondi evitano collisioni quando due esportazioni
   * vengono effettuate nello stesso secondo.
   */
  private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern(
          "yyyy-MM-dd_HH-mm-ss-SSS"
      );

  /*
   * Gson è thread-safe dopo la configurazione,
   * quindi possiamo riutilizzare una singola istanza.
   */
  private static final Gson GSON =
      new GsonBuilder()
          .setPrettyPrinting()
          .serializeNulls()
          .create();

  public void write(
      @NotNull Project project,
      @NotNull String fileName,
      @NotNull JsonElement json
  ) {

    /*
     * Serializzazione e scrittura vengono eseguite
     * fuori dal thread dell'interfaccia.
     */
    ApplicationManager
        .getApplication()
        .executeOnPooledThread(() ->
            writeInBackground(
                project,
                fileName,
                json
            )
        );
  }

  private void writeInBackground(
      @NotNull Project project,
      @NotNull String fileName,
      @NotNull JsonElement json
  ) {

    if (project.isDisposed()) {
      return;
    }

    try {

      String basePath = project.getBasePath();

      if (basePath == null) {

        showError(
            project,
            "Il progetto non ha una directory di base."
        );

        return;
      }

      Path outputFolder =
          Path.of(
              basePath,
              OUTPUT_DIRECTORY
          );

      Files.createDirectories(outputFolder);

      String safeFileName =
          sanitizeFileName(fileName);

      String timestamp =
          LocalDateTime.now().format(
              FILE_TIMESTAMP_FORMAT
          );

      Path outputFile =
          outputFolder.resolve(
              safeFileName
                  + "_"
                  + timestamp
                  + ".json"
          );

      String jsonContent =
          GSON.toJson(json);

      Files.writeString(
          outputFile,
          jsonContent,
          StandardCharsets.UTF_8
      );

      refreshAndNotify(
          project,
          outputFile
      );

    } catch (Exception exception) {

      LOG.warn(
          "Errore durante la scrittura del file JSON",
          exception
      );

      showError(
          project,
          "Errore durante la scrittura del JSON:\n"
              + getErrorMessage(exception)
      );
    }
  }

  private void refreshAndNotify(
      @NotNull Project project,
      @NotNull Path outputFile
  ) {

    /*
     * Il Virtual File System e i componenti grafici
     * devono essere aggiornati sul thread UI.
     */
    ApplicationManager
        .getApplication()
        .invokeLater(() -> {

          if (project.isDisposed()) {
            return;
          }

          VirtualFile virtualFile =
              LocalFileSystem
                  .getInstance()
                  .refreshAndFindFileByNioFile(
                      outputFile
                  );

          if (virtualFile != null) {
            virtualFile.refresh(
                false,
                false
            );
          }

          Messages.showInfoMessage(
              project,
              "JSON creato in:\n"
                  + outputFile.toAbsolutePath(),
              DIALOG_TITLE
          );
        });
  }

  private void showError(
      @NotNull Project project,
      @NotNull String message
  ) {

    ApplicationManager
        .getApplication()
        .invokeLater(() -> {

          if (project.isDisposed()) {
            return;
          }

          Messages.showErrorDialog(
              project,
              message,
              DIALOG_TITLE
          );
        });
  }

  private String sanitizeFileName(
      @NotNull String fileName
  ) {

    if (fileName.isBlank()) {
      return "Object";
    }

    String sanitized =
        fileName.replaceAll(
            "[^a-zA-Z0-9._-]",
            "_"
        );

    return sanitized.isBlank()
        ? "Object"
        : sanitized;
  }

  private String getErrorMessage(
      @NotNull Exception exception
  ) {

    String message = exception.getMessage();

    if (message == null || message.isBlank()) {
      return exception
          .getClass()
          .getSimpleName();
    }

    return message;
  }
}