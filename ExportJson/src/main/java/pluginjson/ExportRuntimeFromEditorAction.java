package pluginjson;

import com.google.gson.JsonElement;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.managerThread.DebuggerCommand;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pluginjson.scanner.JsonExporterService;
import pluginjson.scanner.RuntimeScannerService;

public class ExportRuntimeFromEditorAction extends AnAction {

  private static final Logger LOG =
      Logger.getInstance(
          ExportRuntimeFromEditorAction.class
      );

  private static final String DIALOG_TITLE =
      "Export Object to JSON";

  @Override
  public void actionPerformed(
      @NotNull AnActionEvent event
  ) {

    Project project = event.getProject();
    Editor editor =
        event.getData(CommonDataKeys.EDITOR);

    // Senza progetto o editor non c'è un contesto su cui lavorare.
    if (project == null || editor == null) {
      return;
    }

    // Cerca prima il testo selezionato, poi la variabile sotto il cursore.
    String variableName =
        findSelectedVariable(
            project,
            editor
        );

    if (variableName == null) {

      showWarning(
          project,
          "Seleziona una variabile Java oppure "
              + "posiziona il cursore sopra il suo nome."
      );

      return;
    }

    exportRuntimeValue(
        project,
        variableName
    );
  }

  @Override
  public void update(
      @NotNull AnActionEvent event
  ) {

    Project project = event.getProject();
    Editor editor =
        event.getData(CommonDataKeys.EDITOR);

    PsiFile psiFile =
        event.getData(CommonDataKeys.PSI_FILE);

    // L'azione ha senso soltanto nell'editor di un file Java.
    boolean visible =
        project != null
            && editor != null
            && psiFile instanceof PsiJavaFile;

    // La voce è abilitata soltanto se esiste una sessione di debug.
    boolean enabled =
        visible
            && hasDebuggerSession(project);

    event.getPresentation().setVisible(visible);
    event.getPresentation().setEnabled(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread
  getActionUpdateThread() {

    // Il controllo dello stato dell'azione non richiede il thread UI.
    return ActionUpdateThread.BGT;
  }

  private boolean hasDebuggerSession(
      @Nullable Project project
  ) {

    if (project == null || project.isDisposed()) {
      return false;
    }

    return getDebuggerSession(project) != null;
  }

  private @Nullable DebuggerSession getDebuggerSession(
      @NotNull Project project
  ) {

    return DebuggerManagerEx
        .getInstanceEx(project)
        .getContext()
        .getDebuggerSession();
  }

  private @Nullable String findSelectedVariable(
      @NotNull Project project,
      @NotNull Editor editor
  ) {

    String selectedText =
        editor.getSelectionModel()
            .getSelectedText();

    // Un doppio click seleziona normalmente il nome della variabile.
    if (isValidJavaIdentifier(selectedText)) {
      return selectedText;
    }

    // Se non c'è una selezione valida, controlla il cursore.
    return findVariableUnderCaret(
        project,
        editor
    );
  }

  private boolean isValidJavaIdentifier(
      @Nullable String text
  ) {

    if (text == null || text.isBlank()) {
      return false;
    }

    if (!Character.isJavaIdentifierStart(
        text.charAt(0)
    )) {
      return false;
    }

    for (int index = 1;
        index < text.length();
        index++) {

      if (!Character.isJavaIdentifierPart(
          text.charAt(index)
      )) {
        return false;
      }
    }

    return true;
  }

  private @Nullable String findVariableUnderCaret(
      @NotNull Project project,
      @NotNull Editor editor
  ) {

    PsiFile psiFile =
        PsiDocumentManager
            .getInstance(project)
            .getPsiFile(
                editor.getDocument()
            );

    if (!(psiFile instanceof PsiJavaFile)) {
      return null;
    }

    PsiElement element =
        findElementUnderCaret(
            psiFile,
            editor
        );

    if (element == null) {
      return null;
    }

    PsiIdentifier identifier =
        findIdentifier(element);

    if (identifier == null) {
      return null;
    }

    PsiElement parent =
        identifier.getParent();

    // Gestisce una dichiarazione come: Customer customer = ...
    if (parent instanceof PsiVariable variable) {
      return variable.getName();
    }

    // Gestisce un utilizzo come: System.out.println(customer)
    if (parent
        instanceof PsiReferenceExpression reference) {

      PsiElement resolvedElement =
          reference.resolve();

      if (resolvedElement
          instanceof PsiVariable variable) {

        return variable.getName();
      }
    }

    return null;
  }

  private @Nullable PsiElement findElementUnderCaret(
      @NotNull PsiFile psiFile,
      @NotNull Editor editor
  ) {

    int offset =
        editor.getCaretModel()
            .getOffset();

    PsiElement element =
        psiFile.findElementAt(offset);

    /*
     * Se il cursore si trova immediatamente dopo il nome,
     * provo anche il carattere precedente.
     */
    if (element == null && offset > 0) {

      element =
          psiFile.findElementAt(
              offset - 1
          );
    }

    return element;
  }

  private @Nullable PsiIdentifier findIdentifier(
      @NotNull PsiElement element
  ) {

    if (element instanceof PsiIdentifier identifier) {
      return identifier;
    }

    return PsiTreeUtil.getParentOfType(
        element,
        PsiIdentifier.class,
        false
    );
  }

  private void exportRuntimeValue(
      @NotNull Project project,
      @NotNull String variableName
  ) {

    DebuggerSession debuggerSession =
        getDebuggerSession(project);

    if (debuggerSession == null) {

      showWarning(
          project,
          "Non è presente una sessione di debug attiva."
      );

      return;
    }

    /*
     * Il frame corrente contiene le variabili locali
     * visibili nel punto in cui il debugger è sospeso.
     */
    StackFrameProxyImpl frameProxy =
        debuggerSession
            .getContextManager()
            .getContext()
            .getFrameProxy();

    if (frameProxy == null) {

      showWarning(
          project,
          "Il debugger deve essere fermo "
              + "su un breakpoint."
      );

      return;
    }

    // Prende il processo di debug.
    DebugProcessImpl debugProcess =
        debuggerSession.getProcess();

    DebuggerManagerThreadImpl managerThread =
        debugProcess.getManagerThread();

    /*
     * Le operazioni JDI devono essere eseguite
     * sul manager thread del debugger.
     */
    managerThread.invokeCommand(
        new DebuggerCommand() {

          @Override
          public void action() {

            readAndExportVariable(
                project,
                frameProxy,
                variableName
            );
          }

          @Override
          public void commandCancelled() {

            LOG.debug(
                "Esportazione annullata per la variabile: "
                    + variableName
            );
          }
        }
    );
  }

  private void readAndExportVariable(
      @NotNull Project project,
      @NotNull StackFrameProxyImpl frameProxy,
      @NotNull String variableName
  ) {

    try {

      // Cerca la variabile nello stack frame attualmente selezionato.
      LocalVariableProxyImpl localVariable =
          frameProxy.visibleVariableByName(
              variableName
          );

      if (localVariable == null) {

        showWarning(
            project,
            "La variabile \""
                + variableName
                + "\" non è visibile "
                + "nello stack frame corrente."
        );

        return;
      }

      // Recupera il valore reale presente nella JVM in debug.
      Value runtimeValue =
          frameProxy.getValue(
              localVariable
          );

      if (runtimeValue == null) {

        showWarning(
            project,
            "La variabile \""
                + variableName
                + "\" vale null."
        );

        return;
      }

      // Converte il valore JDI in un elemento JSON.
      RuntimeScannerService scanner =
          new RuntimeScannerService();

      JsonElement json =
          scanner.scanValue(runtimeValue);

      exportJson(
          project,
          variableName,
          json
      );

    } catch (Exception exception) {

      LOG.warn(
          "Impossibile esportare la variabile "
              + variableName,
          exception
      );

      showWarning(
          project,
          "Errore durante l'esportazione "
              + "della variabile \""
              + variableName
              + "\":\n"
              + getErrorMessage(exception)
      );
    }
  }

  private void exportJson(
      @NotNull Project project,
      @NotNull String variableName,
      @NotNull JsonElement json
  ) {

    /*
     * L'exporter gestisce internamente la scrittura
     * su un thread in background.
     */
    new JsonExporterService()
        .write(
            project,
            variableName,
            json
        );
  }

  private void showWarning(
      @NotNull Project project,
      @NotNull String message
  ) {

    // I dialog di IntelliJ devono essere mostrati sul thread UI.
    ApplicationManager
        .getApplication()
        .invokeLater(() -> {

          if (project.isDisposed()) {
            return;
          }

          Messages.showWarningDialog(
              project,
              message,
              DIALOG_TITLE
          );
        });
  }

  private String getErrorMessage(
      @NotNull Exception exception
  ) {

    String message =
        exception.getMessage();

    if (message == null || message.isBlank()) {

      return exception
          .getClass()
          .getSimpleName();
    }

    return message;
  }
}