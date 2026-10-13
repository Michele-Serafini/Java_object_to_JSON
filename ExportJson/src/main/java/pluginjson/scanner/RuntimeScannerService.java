package pluginjson.scanner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.ByteValue;
import com.sun.jdi.CharValue;
import com.sun.jdi.ClassType;
import com.sun.jdi.DoubleValue;
import com.sun.jdi.Field;
import com.sun.jdi.FloatValue;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.LongValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ShortValue;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RuntimeScannerService {

  // Impedisce di attraversare grafi di oggetti troppo profondi.
  private static final int MAX_DEPTH = 100;

  // Evita di esportare array con un numero eccessivo di elementi.
  private static final int MAX_ARRAY_ITEMS = 100000;

  // Limita il numero totale di oggetti analizzati.
  private static final int MAX_OBJECTS = 1000000;

  // Contiene gli ID degli oggetti già visitati per evitare cicli.
  private final Set<Long> visitedObjects = new HashSet<>();

  private int scannedObjectCount;

  /**
   * Punto di ingresso dello scanner.
   */
  public JsonElement scanValue(
      @Nullable Value value
  ) {

    return scanValue(value, 0);
  }

  /**
   * Converte ricorsivamente un valore JDI in un elemento JSON.
   */
  private JsonElement scanValue(
      @Nullable Value value,
      int depth
  ) {

    if (value == null) {
      return JsonNull.INSTANCE;
    }

    if (depth > MAX_DEPTH) {
      return createTruncatedObject(
          "Maximum depth reached"
      );
    }

    return switch (value) {
      case StringReference stringValue -> new JsonPrimitive(
          stringValue.value()
      );
      case BooleanValue booleanValue -> new JsonPrimitive(
          booleanValue.booleanValue()
      );
      case ByteValue byteValue -> new JsonPrimitive(
          byteValue.byteValue()
      );
      case ShortValue shortValue -> new JsonPrimitive(
          shortValue.shortValue()
      );
      case IntegerValue integerValue -> new JsonPrimitive(
          integerValue.intValue()
      );
      case LongValue longValue -> new JsonPrimitive(
          longValue.longValue()
      );
      case FloatValue floatValue -> new JsonPrimitive(
          floatValue.floatValue()
      );
      case DoubleValue doubleValue -> new JsonPrimitive(
          doubleValue.doubleValue()
      );
      case CharValue charValue -> new JsonPrimitive(
          String.valueOf(
              charValue.charValue()
          )
      );
      case ArrayReference arrayReference -> scanArray(
          arrayReference,
          depth
      );
      case ObjectReference objectReference -> scanObjectValue(
          objectReference,
          depth
      );
      default ->

        // Fallback per eventuali tipi JDI non gestiti esplicitamente.
          new JsonPrimitive(
              value.toString()
          );
    };

  }

  /**
   * Decide come trattare un riferimento a oggetto.
   */
  private JsonElement scanObjectValue(
      @NotNull ObjectReference objectReference,
      int depth
  ) {

    JsonElement wrapperValue =
        scanBoxedPrimitive(
            objectReference,
            depth
        );

    if (wrapperValue != null) {
      return wrapperValue;
    }

    JsonElement enumValue =
        scanEnum(objectReference);

    if (enumValue != null) {
      return enumValue;
    }

    return scanObject(
        objectReference,
        depth
    );
  }

  /**
   * Esporta ricorsivamente i campi di un oggetto.
   */
  private JsonObject scanObject(
      @NotNull ObjectReference objectReference,
      int depth
  ) {

    JsonObject json = new JsonObject();

    long objectId =
        objectReference.uniqueID();

    /*
     * Se l'oggetto è già stato visitato, restituisce un riferimento
     * invece di entrare in un ciclo infinito.
     */
    if (!visitedObjects.add(objectId)) {

      json.addProperty(
          "$ref",
          objectReference
              .referenceType()
              .name()
              + "#"
              + objectId
      );

      return json;
    }

    scannedObjectCount++;

    if (scannedObjectCount > MAX_OBJECTS) {
      return createTruncatedObject(
          "Maximum object count reached"
      );
    }

    // Tipo oggetto
    ReferenceType referenceType =
        objectReference.referenceType();

    for (Field field : referenceType.allFields()) {

      // I campi statici non appartengono all'istanza.
      if (field.isStatic()) {
        continue;
      }

      // I campi sintetici sono generati dal compilatore.
      if (field.isSynthetic()) {
        continue;
      }

      try {

        Value fieldValue =
            objectReference.getValue(field);

        json.add(
            field.name(),
            scanValue(
                fieldValue,
                depth + 1
            )
        );

      } catch (Exception exception) {

        /*
         * Un campo non leggibile non deve impedire
         * l'esportazione del resto dell'oggetto.
         */
        JsonObject error = new JsonObject();

        error.addProperty(
            "$error",
            getErrorMessage(exception)
        );

        json.add(
            field.name(),
            error
        );
      }
    }

    return json;
  }

  /**
   * Converte un array Java in un array JSON.
   */
  private JsonArray scanArray(
      @NotNull ArrayReference arrayReference,
      int depth
  ) {

    JsonArray jsonArray = new JsonArray();

    int originalLength =
        arrayReference.length();

    int exportedLength =
        Math.min(
            originalLength,
            MAX_ARRAY_ITEMS
        );

    if (exportedLength > 0) {

      for (Value arrayValue :
          arrayReference.getValues(
              0,
              exportedLength
          )) {

        jsonArray.add(
            scanValue(
                arrayValue,
                depth + 1
            )
        );
      }
    }

    /*
     * Se l'array è stato troncato, aggiunge
     * un elemento informativo alla fine.
     */
    if (originalLength > exportedLength) {

      JsonObject truncated = new JsonObject();

      truncated.addProperty(
          "$truncated",
          originalLength
              - exportedLength
              + " additional items"
      );

      jsonArray.add(truncated);
    }

    return jsonArray;
  }

  /**
   * Converte i wrapper Java, come Integer e Boolean,
   * nei corrispondenti valori JSON.
   */
  private @Nullable JsonElement scanBoxedPrimitive(
      @NotNull ObjectReference objectReference,
      int depth
  ) {

    String typeName =
        objectReference
            .referenceType()
            .name();

    if ("java.lang.String".equals(typeName)
        && objectReference
        instanceof StringReference stringReference) {

      return new JsonPrimitive(
          stringReference.value()
      );
    }

    if (!isSupportedWrapper(typeName)) {
      return null;
    }

    Field valueField =
        findField(
            objectReference.referenceType(),
            "value"
        );

    if (valueField == null) {
      return null;
    }

    Value wrappedValue =
        objectReference.getValue(
            valueField
        );

    return scanValue(
        wrappedValue,
        depth + 1
    );
  }

  /**
   * Indica quali wrapper Java possono essere convertiti direttamente.
   */
  private boolean isSupportedWrapper(
      @NotNull String typeName
  ) {

    return "java.lang.Boolean".equals(typeName)
        || "java.lang.Byte".equals(typeName)
        || "java.lang.Short".equals(typeName)
        || "java.lang.Integer".equals(typeName)
        || "java.lang.Long".equals(typeName)
        || "java.lang.Float".equals(typeName)
        || "java.lang.Double".equals(typeName)
        || "java.lang.Character".equals(typeName);
  }

  /**
   * Converte un enum nel nome della costante.
   */
  private @Nullable JsonElement scanEnum(
      @NotNull ObjectReference objectReference
  ) {

    if (!isEnumType(objectReference)) {
      return null;
    }

    Field nameField =
        findField(
            objectReference.referenceType(),
            "name"
        );

    if (nameField == null) {
      return null;
    }

    Value nameValue =
        objectReference.getValue(
            nameField
        );

    if (nameValue
        instanceof StringReference stringReference) {

      return new JsonPrimitive(
          stringReference.value()
      );
    }

    return null;
  }

  /**
   * Verifica se il tipo estende java.lang.Enum.
   */
  private boolean isEnumType(
      @NotNull ObjectReference objectReference
  ) {

    if (!(objectReference.referenceType()
        instanceof ClassType classType)) {

      return false;
    }

    ClassType superClass =
        classType.superclass();

    while (superClass != null) {

      if ("java.lang.Enum".equals(
          superClass.name()
      )) {
        return true;
      }

      superClass =
          superClass.superclass();
    }

    return false;
  }

  /**
   * Cerca un campo includendo anche quelli ereditati.
   */
  private @Nullable Field findField(
      @NotNull ReferenceType referenceType,
      @NotNull String fieldName
  ) {

    for (Field field : referenceType.allFields()) {

      if (fieldName.equals(field.name())) {
        return field;
      }
    }

    return null;
  }

  /**
   * Crea un elemento informativo quando lo scanner
   * raggiunge uno dei limiti configurati.
   */
  private JsonObject createTruncatedObject(
      @NotNull String reason
  ) {

    JsonObject json = new JsonObject();

    json.addProperty(
        "$truncated",
        reason
    );

    return json;
  }

  /**
   * Restituisce un messaggio leggibile anche quando
   * l'eccezione non contiene una descrizione.
   */
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