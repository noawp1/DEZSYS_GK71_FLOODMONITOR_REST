# DEZSYS GK71 - Flood Monitor REST

Schulprojekt im Fach Dezentrale Systeme: Eine Spring-Boot-Anwendung für ein Hochwasser-Frühwarnsystem mit REST-Schnittstelle, XML/JSON-Unterstützung und Webclient.

---

## 1. Worum geht es?

In dem Projekt geht es darum, Messstationen an Flüssen (wie Donau, Inn und Salzach) zu verwalten. Die Stationen messen regelmäßig den Wasserstand, wie viel Wasser durchfließt, wie viel es regnet, die Temperatur und den Akkustand.

Das Backend erzeugt über eine Simulation regelmäßig neue Werte, berechnet automatisch die passende Warnstufe (NORMAL, WARNING, CRITICAL oder UNKNOWN) und stellt alle Daten über eine REST-API bereit. Über den Webclient kann man sich die aktuellen Werte, Statistiken und den Verlauf in einer Tabelle ansehen.

---

## 2. Aufbau des Projekts

Ich habe den Code in die typischen Spring-Boot-Packages aufgeteilt, damit alles übersichtlich bleibt:

- **`model/`**: Die Datenklassen (`Station`, `Measurement`, `Alert`, `StationStatistics`) und Enums (`WarningLevel`, `StationStatus`).
- **`repository/`**: Speichert die Stationen und die Messungen im Arbeitsspeicher (in `ConcurrentHashMap`-Listen), damit man keine externe Datenbank einrichten muss.
- **`service/`**:
  - `SimulationService`: Erzeugt alle 10 Sekunden neue Messwerte und berechnet die Warnstufe.
  - `StationService`: Prüft Eingaben, filtert Daten und berechnet die Statistiken.
- **`controller/`**:
  - `StationController`: Kümmert sich um alle Anfragen unter `/api/v1/stations/**`.
  - `AlertController`: Liefert aktive Warnungen unter `/api/v1/alerts`.
- **`exception/`**:
  - `GlobalExceptionHandler`: Fängt Fehler zentral ab (z. B. wenn eine Station nicht existiert oder Parameter falsch sind) und gibt eine saubere JSON- oder XML-Fehlermeldung zurück.
- **`static/`**:
  - `index.html`: Der Webclient mit HTML, CSS und jQuery.

---

## 3. Datenmodell

### Messstation (`Station`)
- `id`: Eindeutige Kennung (z. B. `ST-001`)
- `name`: Name der Station (z. B. `Station Wien - Reichsbrücke`)
- `river`: Fluss (z. B. `Donau`)
- `location`: Koordinaten (`latitude`, `longitude`)
- `normalWaterLevel`: Normaler Wasserstand in cm (z. B. 260 cm)
- `warningWaterLevel`: Warnwert in cm (z. B. 500 cm)
- `criticalWaterLevel`: Kritischer Wert in cm (z. B. 700 cm)
- `isActive`: Ob die Station aktiv ist (`true`/`false`)

### Messung (`Measurement`)
- `stationId`: Zu welcher Station die Messung gehört
- `timestamp`: Zeitpunkt im ISO-8601-Format (UTC)
- `waterLevel`: Wasserstand in cm
- `flowRate`: Durchflussmenge in m³/s
- `rainfall`: Regenmenge in mm/h
- `temperature`: Temperatur in °C
- `batteryLevel`: Akkustand in % (0 - 100)
- `status`: `ONLINE`, `MAINTENANCE` oder `OFFLINE`
- `warningLevel`: `NORMAL`, `WARNING`, `CRITICAL` oder `UNKNOWN`

---

## 4. Wie funktioniert die Simulation?

Im `SimulationService` läuft eine Methode mit `@Scheduled(fixedRateString = "${simulation.interval:10000}")`, die alle 10 Sekunden für jede aktive Station einen neuen Messwert erzeugt:

1. **Bezug zum vorherigen Wert:** Der neue Wasserstand wird nicht komplett neu ausgewürfelt, sondern verändert sich leicht ausgehend vom letzten Wert (+/- 2 cm Schwankung).
2. **Einfluss von Regen:** Wenn es mehr als 4 mm/h regnet, steigt der Pegel an. Wenn es trocken ist, sinkt er langsam wieder in Richtung Normalwert.
3. **Durchflussmenge:** Ein höherer Wasserstand führt logisch auch zu mehr Durchfluss (`flowRate ≈ waterLevel * 2.3`).
4. **Grenzen:** Der Wasserstand kann nicht unter 0 fallen. Der Akku verliert mit der Zeit leicht an Ladung.
5. **Wartung:** Fällt der Akku unter 15%, wechselt der Status der Station automatisch auf `MAINTENANCE`.
6. **Inaktive Stationen:** Wenn eine Station auf `isActive = false` gesetzt ist oder `OFFLINE` ist, werden keine neuen Messwerte erzeugt.

### Berechnung der Warnstufen:
- **`UNKNOWN`**: Wenn der Status nicht `ONLINE` ist (z. B. bei Wartung oder Ausfall) oder noch keine Messung vorliegt.
- **`CRITICAL`**: Sobald der Wasserstand `>= criticalWaterLevel` ist.
- **`WARNING`**: Sobald der Wasserstand `>= warningWaterLevel` ist (aber noch unter dem kritischen Wert liegt).
- **`NORMAL`**: Wenn der Wasserstand unter dem Warnwert liegt.

---

## 5. JSON und XML (Content Negotiation)

Die API liefert standardmäßig JSON. Der Client kann aber über den HTTP-Header `Accept` selbst bestimmen, welches Format er haben will:

- `Accept: application/json` $\rightarrow$ JSON-Antwort
- `Accept: application/xml` $\rightarrow$ XML-Antwort
- Kein Header angegeben $\rightarrow$ Standardmäßig JSON
- Anderer Header (z. B. `Accept: image/png`) $\rightarrow$ HTTP-Statuscode `406 Not Acceptable`

### Beispiel JSON (`GET /api/v1/stations/ST-001/measurements/latest`):
```json
{
  "stationId": "ST-001",
  "timestamp": "2026-11-03T10:15:00Z",
  "waterLevel": 278.4,
  "flowRate": 640.3,
  "rainfall": 5.2,
  "temperature": 15.6,
  "batteryLevel": 91,
  "status": "ONLINE",
  "warningLevel": "NORMAL"
}
```

### Beispiel XML (`GET /api/v1/stations/ST-001/measurements/latest`):
```xml
<measurement>
  <stationId>ST-001</stationId>
  <timestamp>2026-11-03T10:15:00Z</timestamp>
  <waterLevel>278.4</waterLevel>
  <flowRate>640.3</flowRate>
  <rainfall>5.2</rainfall>
  <temperature>15.6</temperature>
  <batteryLevel>91</batteryLevel>
  <status>ONLINE</status>
  <warningLevel>NORMAL</warningLevel>
</measurement>
```

---

## 6. Wichtige API-Endpunkte

| Methode | URL | Was macht der Endpunkt? |
|---|---|---|
| `GET` | `/api/v1/stations` | Alle Stationen auflisten (Filter: `river`, `isActive`, `warningLevel`) |
| `GET` | `/api/v1/stations/{id}` | Einzelne Station abrufen |
| `POST` | `/api/v1/stations` | Neue Station anlegen (201 Created) |
| `PATCH` | `/api/v1/stations/{id}` | Station bearbeiten (z. B. deaktivieren) |
| `DELETE` | `/api/v1/stations/{id}` | Station löschen (204 No Content) |
| `GET` | `/api/v1/stations/{id}/measurements/latest` | Den aktuellsten Messwert holen |
| `GET` | `/api/v1/stations/{id}/measurements` | Messhistorie (Filter: `from`, `to`, `limit`, `warningLevel`) |
| `GET` | `/api/v1/stations/{id}/statistics` | Statistiken (Min, Max, Durchschnitt, Regenmenge) |
| `GET` | `/api/v1/alerts` | Alle Stationen mit aktiven Warnungen (WARNING/CRITICAL) |

### Testen mit curl:
```bash
# 1. Alle Stationen an der Donau abrufen
curl "http://localhost:8080/api/v1/stations?river=Donau"

# 2. Aktuellen Messwert als XML anfordern
curl -H "Accept: application/xml" "http://localhost:8080/api/v1/stations/ST-001/measurements/latest"

# 3. Messwerte mit Zeitraum und Limit filtern
curl "http://localhost:8080/api/v1/stations/ST-001/measurements?limit=10&warningLevel=NORMAL"
```

---

## 7. Fehlerbehandlung

Wenn eine Anfrage ungültig ist, wirft die Anwendung eine passende Exception, die vom `GlobalExceptionHandler` abgefangen wird. Der Client bekommt ein sauberes Fehler-Objekt ohne Java-Stacktrace:

```json
{
  "timestamp": "2026-11-03T10:20:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Station 'ST-999' wurde nicht gefunden",
  "path": "/api/v1/stations/ST-999"
}
```

Behandelte Fälle:
- `400 Bad Request`: Wenn `from` nach `to` liegt, ein Datumsformat ungültig ist, die Warnstufe nicht existiert oder das Limit kleiner 1 bzw. größer 1000 ist.
- `404 Not Found`: Wenn eine Stations-ID nicht existiert.
- `406 Not Acceptable`: Wenn der Client im `Accept`-Header ein nicht unterstütztes Format verlangt.

---

## 8. Webclient

Der Webclient befindet sich in `src/main/resources/static/index.html` und kann direkt über `http://localhost:8080` aufgerufen werden.

Funktionen im Webclient:
- **Station auswählen:** Dropdown lädt alle Stationen vom Backend.
- **Umschalten zwischen JSON und XML:**
  - Bei JSON wird die JSON-Antwort direkt verarbeitet.
  - Bei XML sendet jQuery `Accept: application/xml`, liest die Werte mit `$(xml).find(...)` aus und zeigt dieselbe Tabelle an.
- **Farben für Warnstufen:**
  - NORMAL = Grün
  - WARNING = Orange
  - CRITICAL = Rot
  - UNKNOWN = Grau
- **Filter:** Man kann nach Zeitraum (`from` / `to`), Warnstufe und Limit filtern.
- **Auto-Refresh:** Aktualisiert die Werte alle 5 Sekunden automatisch per AJAX.

---

## 9. Tests ausführen

Im Testverzeichnis (`src/test/java/...`) sind alle geforderten Tests mit JUnit 5 und MockMvc umgesetzt:
- JSON- und XML-Abruf aller Stationen
- Abruf einzelner Stationen (inkl. 404 bei unbekannten IDs)
- Neueste Messung und Filterung nach Zeitraum
- Validierung von ungültigen Zeiträumen (`from` nach `to` $\rightarrow$ 400)
- Schwellenwert-Logik für NORMAL, WARNING und CRITICAL
- Nicht unterstütztes Format (`Accept: image/png` $\rightarrow$ 406)
- Maximales Limit ($\le 0$ oder $> 1000$ $\rightarrow$ 400)

Um die Tests zu starten:
```bash
./gradlew test
```
*Hinweis:* In den Tests ist die Simulation mit `@TestPropertySource(properties = {"simulation.enabled=false"})` ausgeschaltet, damit die Messwerte sich während des Tests nicht unvorhersehbar verändern.

---

## 10. Startanleitung

Voraussetzung: Java 17 oder 21.

```bash
# Anwendung starten
./gradlew bootRun
```

Danach erreichbar unter:
- Webclient: [http://localhost:8080](http://localhost:8080)
- Swagger UI (OpenAPI-Doku): [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

## 11. Antworten zu den Fragestellungen (Punkt 12)

### 1. Was sind die Hauptmerkmale von JSON?
- Einfaches, textbasiertes Format zum Datenaustausch (JavaScript Object Notation).
- Basiert auf Objekten mit Schlüssel-Wert-Paaren (`{ "key": "value" }`) und Listen/Arrays (`[ ... ]`).
- Unterstützt Grunddatentypen direkt (String, Zahl, Boolean, null).
- Braucht weniger Zeichen als XML, weil es keine schließenden Tags gibt.
- Kann in JavaScript direkt mit `JSON.parse()` gelesen werden.  
*Quelle:* RFC 8259 (https://datatracker.ietf.org/doc/html/rfc8259)

### 2. Was sind die Hauptmerkmale von XML?
- Auszeichnungssprache (Extensible Markup Language) mit öffnenden und schließenden Tags (`<station>...</station>`) in einer Baumstruktur.
- Unterstützt zusätzliche Attribute in Tags (`<station id="1">`).
- Kann mit Schemas (XSD oder DTD) streng auf Typen und Struktur geprüft werden.
- Unterstützt Namespaces, damit gleiche Tagnamen aus verschiedenen Bereichen nicht kollidieren.  
*Quelle:* W3C XML 1.0 Recommendation (https://www.w3.org/TR/xml/)

### 3. Wie unterscheidet die Applikation, ob JSON oder XML zurückgegeben wird?
Das läuft über HTTP Content Negotiation mit dem Header `Accept`.
Wenn der Client `Accept: application/xml` mitschickt, verwendet Spring Boot den `MappingJackson2XmlHttpMessageConverter`. Schickt er `Accept: application/json` oder gar nichts, greift der `MappingJackson2HttpMessageConverter` und gibt JSON zurück. Fordert der Client etwas anderes an, gibt es einen 406-Fehler.  
*Quelle:* Spring MVC Content Negotiation Docs (https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/content-negotiation.html)

### 4. Was zeichnet ein REST Interface aus und warum wird es heute so oft verwendet?
REST (Representational State Transfer nach Roy Fielding) hat klare Regeln:
- **Client-Server-Trennung:** Frontend und Backend sind voneinander unabhängig.
- **Zustandslosigkeit:** Jede Anfrage enthält alle Infos; der Server speichert keine Session.
- **Einheitliche Schnittstelle:** Ressourcen werden über eindeutige URLs angesprochen und Standard-HTTP-Methoden (GET, POST, PUT, DELETE) genutzt.
- **Cachebarkeit:** Antworten können zwischengespeichert werden.  
Es ist so beliebt, weil es einfach über normales HTTP funktioniert, auf jedem System läuft und leicht verständlich ist.  
*Quelle:* Roy Fielding Dissertation, 2000 (https://www.ics.uci.edu/~fielding/pubs/dissertation/rest_arch_style.htm)

### 5. Was ist der Unterschied zwischen GET, POST, PUT und PATCH?
- **`GET`**: Nur Daten abrufen. Verändert nichts auf dem Server ("safe") und liefert bei gleichem Aufruf das gleiche Ergebnis ("idempotent").
- **`POST`**: Erstellt eine neue Ressource oder schickt Daten ab. Ist weder safe noch idempotent (zweimal aufrufen erzeugt zwei Einträge).
- **`PUT`**: Ersetzt eine bestehende Ressource komplett unter der angegebenen URL. Ist idempotent.
- **`PATCH`**: Ändert nur einzelne Felder einer Ressource (z. B. nur `isActive` auf false).  
*Quelle:* RFC 9110 HTTP Semantics, Abschnitt 9.3

### 6. Welche Stellen im Code muss ich ändern, um einen neuen Request-Pfad anzubieten?
1. Im passenden **Controller** eine neue Methode mit Annotation (z. B. `@GetMapping("/pfad")`) anlegen.
2. Im **Service** die passende Methode mit der Logik schreiben.
3. Falls neue Daten nötig sind, das **Model** oder **Repository** anpassen.
4. Einen Test im Testverzeichnis ergänzen, um sicherzustellen, dass die neue Route funktioniert.

### 7. Kann man OpenAPI und Swagger vergleichen?
Ja. Kurz gesagt:
- **OpenAPI** ist die herstellerunabhängige **Spezifikation** (eine formale Beschreibung der API in YAML/JSON).
- **Swagger** ist die **Tool-Sammlung** (von SmartBear), die diese Spezifikation visualisiert und nutzt (z. B. Swagger UI als Weboberfläche oder Swagger Editor).  
OpenAPI ist also der Bauplan/Standard, Swagger sind die Werkzeuge dazu.  
*Quelle:* OpenAPI Initiative (https://www.openapis.org/about)

### 8. Wo liegt im Code-Snippet der Fehler?
```java
@GetMapping("/stations/add")
public String createStation(@RequestParam String name, @RequestParam double warning) {
    stationService.save(new Station(name, warning));
    return "Station successfully created!";
}
```
**Fehler:**
1. **Falsche HTTP-Methode:** Zum Erstellen von Ressourcen muss `@PostMapping` verwendet werden, nicht `@GetMapping`. GET darf keine Daten auf dem Server verändern.
2. **Falsche URL:** In REST nutzt man Substantive im Plural (`/stations`), keine Verben oder Aktionen (`/add`). Die Aktion wird über die HTTP-Methode definiert (`POST /stations`).
3. **Parameterübergabe:** Die Daten sollten als strukturierter JSON-Body (`@RequestBody Station station`) übermittelt werden, nicht einzeln in der URL-Query (`@RequestParam`).
4. **Falscher Rückgabewert:** Eine REST-API sollte den Statuscode `201 Created` mit dem erstellten Objekt zurückgeben, nicht normalen Freitext mit Status 200.

---

## 12. Aufgetretene Probleme und Lösungen

1. **Java 25 Inkompatibilität mit Gradle 8.7:**  
   Beim ersten Test-Build gab es einen Fehler (`Unsupported class file major version 69`), weil auf dem System eine neuere Java-Version installiert war. Ich habe den Gradle-Wrapper auf 8.12 aktualisiert und die Java-Toolchain in `build.gradle` fest auf Java 21 konfiguriert.
2. **XML-Ausgabe mit sprechenden Tags:**  
   Standardmäßig hat Jackson XML kryptische Tags erzeugt. Durch die Annotation `@JacksonXmlRootElement(localName = "measurement")` erzeugt Jackson jetzt die exakt geforderten Tags `<measurement>` und `<station>`.
3. **Simulation während automatisierter Tests:**  
   Weil der Timer im Hintergrund alle 10 Sekunden neue Daten erzeugt hat, waren manche Filtertests nicht stabil. Ich habe eine Eigenschaft `simulation.enabled` eingebaut, mit der die Simulation in den Tests abgeschaltet wird.

---

## 13. Verwendete KI-Prompts (zur Dokumentation)

Für die Umsetzung des Projekts wurden folgende Prompts zur Unterstützung genutzt:

1. **Dokumentation & Struktur:**
   - *"Schreibe mir eine ausführliche und strukturierte Projektdokumentation für ein Hochwasser-Frühwarnsystem mit Spring Boot, Content Negotiation (JSON/XML) und Simulation."*
2. **Webclient (`index.html`):**
   - *"Erstelle mir eine einfache `index.html` mit CSS und jQuery, die Stationen und Messdaten von einer REST-API abruft, zwischen JSON und XML umschalten kann, Warnstufen farblich kennzeichnet und die Werte alle 5 Sekunden automatisch aktualisiert."*
3. **Recherchen zu technischen Details:**
   - *"Wie bindet man jackson-dataformat-xml in Spring Boot 3 mit Gradle ein und wie sorgt man dafür, dass das Root-Element @JacksonXmlRootElement heißt?"*
   - *"Wie schreibt man einen Spring Boot MockMvc Test, der den HTTP-Status 406 Not Acceptable bei ungültigem Accept-Header prüft?"*
   - *"Wie liest man mit jQuery per AJAX eine XML-Antwort aus und filtert die Werte mit $(xml).find()?"*
4. **Verständnis & Debugging:**
   - Darüber hinaus wurden während der Entwicklung begleitend Prompts zum besseren Verständnis von Java-Code und Spring-Boot-Konzepten formuliert (z. B. Funktionsweise von `@RestControllerAdvice`, `@Scheduled` Hintergrundtasks, Filterung von Daten mit Java Streams und Behebung von Gradle-Kompilierungsfehlern).
