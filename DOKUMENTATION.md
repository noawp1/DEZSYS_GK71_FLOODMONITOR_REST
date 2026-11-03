# Projektdokumentation: DEZSYS GK71 Flood Monitor REST

**Projekt:** Hochwasser-Frühwarnsystem (REST-API & Webclient)  
**Klasse / Fach:** Dezentrale Systeme (DEZSYS)  
**Autor:** Tobias (noawp1)  
**Technologien:** Java 21, Spring Boot 3.2.0, Gradle 8.12, Jackson XML, jQuery 3.7.1, OpenAPI 3.0  

---

## 1. Aufgabenstellung und Ziel

Ziel dieses Schulprojekts ist die Entwicklung einer Spring-Boot-Anwendung für ein Hochwasser-Frühwarnsystem. An Flüssen wie der Donau, dem Inn und der Salzach erfassen Messstationen zyklisch Wasserstände, Durchflussmengen, Regenmengen, Wassertemperaturen und Akkustände.

Die wichtigsten Anforderungen:
- REST-Schnittstelle unter `/api/v1` für Stationen, Messwerte, Alarme und Statistiken.
- Unterstützung von Content Negotiation: Die Daten müssen sowohl in **JSON** als auch in **XML** über den HTTP-Header `Accept` abrufbar sein.
- Eine serverseitige Messwert-Simulation, die regelmäßig realistische, zusammenhängende Werte berechnet.
- Automatische Berechnung der Warnstufe (`NORMAL`, `WARNING`, `CRITICAL`, `UNKNOWN`).
- Fehlerbehandlung mit passenden HTTP-Statuscodes (400, 404, 406) und einheitlichem Fehlerformat ohne Stacktraces.
- Ein Webclient mit HTML, CSS und jQuery zur Darstellung, Filterung und Umschaltung zwischen JSON und XML.
- Automatisierte Tests mit JUnit 5 und MockMvc.

---

## 2. Architektur und Aufbau

Der Code ist in Schichten gegliedert, um Geschäftslogik, Datenhaltung und REST-Controller sauber zu trennen:

```
src/main/java/com/example/floodmonitor/
├── FloodMonitorApplication.java       # Hauptklasse mit @EnableScheduling
├── config/
│   ├── OpenApiConfig.java             # OpenAPI / Swagger UI Konfiguration
│   └── WebConfig.java                 # Content Negotiation (JSON als Default, XML)
├── controller/
│   ├── StationController.java         # Endpunkte für /api/v1/stations/**
│   └── AlertController.java           # Endpunkt für /api/v1/alerts
├── exception/
│   ├── ApiError.java                  # Fehler-DTO für einheitliche JSON/XML-Fehlermeldungen
│   ├── BadRequestException.java       # Exception für ungültige Eingaben (HTTP 400)
│   ├── ResourceNotFoundException.java # Exception für nicht gefundene Stationen (HTTP 404)
│   └── GlobalExceptionHandler.java    # Zentraler @RestControllerAdvice
├── model/
│   ├── Alert.java                     # Datenklasse für Alarme
│   ├── Location.java                  # Koordinaten (Breite / Länge)
│   ├── Measurement.java               # Messung mit Einheiten und Zeitstempel
│   ├── Station.java                   # Messstation mit Grenzwerten
│   ├── StationStatistics.java         # Statistiken (Min, Max, Durchschnitt)
│   ├── StationStatus.java             # Enum: ONLINE, MAINTENANCE, OFFLINE
│   └── WarningLevel.java              # Enum: NORMAL, WARNING, CRITICAL, UNKNOWN
├── repository/
│   ├── StationRepository.java         # Thread-sicherer In-Memory-Speicher für Stationen
│   └── MeasurementRepository.java     # Thread-sicherer Verlaufsspeicher für Messungen
└── service/
    ├── StationService.java            # Validierung, Filter und Berechnung von Statistiken
    └── SimulationService.java         # Messwertsimulation und Warnstufenberechnung
```

Web-Ressourcen:
- `src/main/resources/static/index.html`: Der Webclient mit HTML, CSS und jQuery.
- `src/main/resources/application.properties`: Port- und Simulationskonfiguration.

---

## 3. Datenmodell

### 3.1 Enums
- **`WarningLevel`**: `NORMAL`, `WARNING`, `CRITICAL`, `UNKNOWN`
- **`StationStatus`**: `ONLINE`, `MAINTENANCE`, `OFFLINE`

### 3.2 Klassen
- **`Station`**: ID (`ST-001`), Name, Fluss, Location (Breite/Länge), Normalpegel in cm, Warnpegel in cm, kritischer Pegel in cm und `isActive`.
- **`Measurement`**: `stationId`, `timestamp` (ISO-8601 Instant), `waterLevel` (cm), `flowRate` (m³/s), `rainfall` (mm/h), `temperature` (°C), `batteryLevel` (%), `status` und `warningLevel`.
- **`Alert`**: Enthält die Station, den aktuellen Wasserstand, die Warnstufe und eine Hinweismeldung.
- **`StationStatistics`**: Berechnet Minimal-, Maximal- und Durchschnittswasserstand, durchschnittlichen Durchfluss, Regensumme sowie Anzahl der Messungen und Warnungen.

Damit XML konforme Tags liefert, sind die Klassen mit `@JacksonXmlRootElement(localName = "...")` annotiert.

---

## 4. Simulation und Warnstufen

### 4.1 Zyklus
Im `SimulationService` erzeugt ein `@Scheduled`-Task standardmäßig alle 10 Sekunden neue Werte für alle aktiven Stationen. Über `simulation.enabled=false` kann dies z. B. während der Tests abgeschaltet werden.

### 4.2 Simulationsregeln
- **Vorwert-Bezug:** Der neue Wasserstand basiert immer auf dem vorherigen Pegel der Station (mit einer leichten natürlichen Schwankung von ca. +/- 2 cm).
- **Regeneinfluss:** Bei starkem Niederschlag ($> 4\text{ mm/h}$) steigt der Pegel an. Ist es trocken, sinkt er allmählich wieder in Richtung Normalwert.
- **Durchfluss:** Ein höherer Wasserstand sorgt automatisch für einen höheren Durchfluss (`flowRate ≈ waterLevel * 2.3`).
- **Grenzen:** Der Pegel kann physikalisch nicht negativ werden (`Math.max(0, ...)`). Der Akku sinkt langsam.
- **Wartungsmodus:** Fällt der Akku unter 15 %, wechselt die Station automatisch auf `MAINTENANCE`.
- **Inaktive Stationen:** Ist `isActive == false` oder `OFFLINE`, werden keine neuen Messungen generiert.

### 4.3 Warnstufen
- **`UNKNOWN`**: Wenn die Station im Status `MAINTENANCE` oder `OFFLINE` ist, oder keine Messung existiert.
- **`CRITICAL`**: Sobald der Wasserstand `>= criticalWaterLevel` ist.
- **`WARNING`**: Sobald der Wasserstand `>= warningWaterLevel` ist.
- **`NORMAL`**: Wenn der Wasserstand unter dem Warnwert liegt.

---

## 5. REST-API und Content Negotiation

### 5.1 Endpunkte (`/api/v1`)

| Methode | Endpunkt | Beschreibung |
|---|---|---|
| `GET` | `/api/v1/stations` | Alle Stationen (Filter: `river`, `isActive`, `warningLevel`) |
| `GET` | `/api/v1/stations/{id}` | Einzelne Station |
| `POST` | `/api/v1/stations` | Station anlegen (201 Created) |
| `PATCH` | `/api/v1/stations/{id}` | Station bearbeiten (z. B. aktivieren/deaktivieren) |
| `DELETE` | `/api/v1/stations/{id}` | Station löschen (204 No Content) |
| `GET` | `/api/v1/stations/{id}/measurements/latest` | Aktuellster Messwert |
| `GET` | `/api/v1/stations/{id}/measurements` | Messhistorie (Filter: `from`, `to`, `limit`, `warningLevel`) |
| `GET` | `/api/v1/stations/{id}/statistics` | Statistische Kennzahlen einer Station |
| `GET` | `/api/v1/alerts` | Aktive Warnungen (Filter: `river`, `warningLevel`) |

### 5.2 Content Negotiation
Über den HTTP-Header `Accept` kann der Client das gewünschte Format wählen:
- `Accept: application/json` $\rightarrow$ JSON-Ausgabe
- `Accept: application/xml` $\rightarrow$ XML-Ausgabe
- Ohne Header $\rightarrow$ Standard: JSON
- Nicht unterstützt (z. B. `Accept: image/png`) $\rightarrow$ HTTP 406 Not Acceptable

Beispiel mit curl:
```bash
# JSON
curl -H "Accept: application/json" http://localhost:8080/api/v1/stations/ST-001/measurements/latest

# XML
curl -H "Accept: application/xml" http://localhost:8080/api/v1/stations/ST-001/measurements/latest
```

---

## 6. Fehlerbehandlung

Ungültige Eingaben werden von `GlobalExceptionHandler` abgefangen und ohne Java-Stacktrace als sauberes Fehler-Objekt ausgegeben:

- `400 Bad Request`: Bei falschem Datumsformat, wenn `from` nach `to` liegt, bei ungültigen Warnstufen oder wenn `limit` außerhalb von 1 bis 1000 liegt.
- `404 Not Found`: Wenn eine Stations-ID nicht im Repository existiert.
- `406 Not Acceptable`: Bei nicht unterstützten Medientypen im `Accept`-Header.

---

## 7. Webclient

Der Webclient liegt unter `src/main/resources/static/index.html`:
- Lädt die Stationen beim Start dynamisch in ein Dropdown.
- Ermöglicht das Umschalten zwischen JSON und XML per Klick.
- Bei XML parst jQuery das XML mit `$(data).find(...)` und stellt exakt dieselbe Tabelle wie bei JSON dar.
- Farbige Badges: NORMAL (grün), WARNING (orange), CRITICAL (rot), UNKNOWN (grau).
- Filter für Zeitraum, Warnstufe und Limit.
- Auto-Refresh alle 5 Sekunden über einen AJAX-Timer.

---

## 8. Tests

Die Testklasse `FloodMonitorApplicationTests` deckt alle 12 geforderten Fälle ab:
1. Alle Stationen als JSON
2. Alle Stationen als XML
3. Existierende Station abrufen
4. Unbekannte Station (404)
5. Aktuellsten Messwert abrufen
6. Zeitraum-Filterung
7. Ungültiger Zeitraum (`from` nach `to` $\rightarrow$ 400)
8. Warnstufe NORMAL
9. Warnstufe WARNING
10. Warnstufe CRITICAL und UNKNOWN
11. Nicht unterstütztes Format (406)
12. Ungültiges Limit (400)
13. Alarme und Statistiken

Ausführen mit:
```bash
./gradlew test
```

---

## 9. Antworten zu den Fragestellungen (Punkt 12)

### 1. Hauptmerkmale von JSON
- Leichtgewichtiges, textbasiertes Datenformat (JavaScript Object Notation).
- Basiert auf Objekten `{}` und Listen/Arrays `[]`.
- Einfache Typen: String, Number, Boolean, null.
- Weniger Overhead als XML, direkt in JavaScript mit `JSON.parse()` nutzbar.  
*Quelle:* RFC 8259 (https://datatracker.ietf.org/doc/html/rfc8259)

### 2. Hauptmerkmale von XML
- Tag-basierte Auszeichnungssprache in hierarchischer Baumstruktur.
- Unterstützt Tags und Attribute.
- Kann über XSD oder DTD strikt validiert werden.
- Unterstützt Namespaces zur Vermeidung von Namenskonflikten.  
*Quelle:* W3C XML Recommendation (https://www.w3.org/TR/xml/)

### 3. Wie unterscheidet die Applikation JSON oder XML?
Über HTTP Content Negotiation anhand des Request-Headers `Accept`. Spring MVC wählt anhand dieses Headers den passenden Converter (`MappingJackson2HttpMessageConverter` für JSON oder `MappingJackson2XmlHttpMessageConverter` für XML).  
*Quelle:* Spring MVC Documentation (https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/content-negotiation.html)

### 4. Was zeichnet ein REST Interface aus?
REST (nach Roy Fielding) basiert auf:
- Client-Server-Trennung
- Zustandslosigkeit (keine Sessions auf dem Server)
- Eindeutigen Ressourcen-URIs und Standard-HTTP-Methoden
- Cachebarkeit der Antworten  
Es ist heute Standard, weil es die bestehende Web-Infrastruktur nutzt, einfach ist und von jeder Programmiersprache aufgerufen werden kann.  
*Quelle:* Fielding, R. T. (2000): Architectural Styles and the Design of Network-based Software Architectures.

### 5. Unterschied GET, POST, PUT, PATCH
- `GET`: Rein lesend, sicher (keine Datenänderung) und idempotent.
- `POST`: Erzeugt eine neue Ressource, weder sicher noch idempotent.
- `PUT`: Überschreibt eine bestehende Ressource vollständig, idempotent.
- `PATCH`: Ändert gezielt einzelne Felder einer Ressource.  
*Quelle:* RFC 9110 HTTP Semantics, Abschnitt 9.3

### 6. Wo muss ich Code ändern für eine neue Route?
1. Im Controller: Neue Methode mit Mapping-Annotation (`@GetMapping` etc.) anlegen.
2. Im Service: Die Logik implementieren.
3. Im Model/Repository: Falls nötig neue Datenstrukturen oder Speicherabfragen ergänzen.
4. Im Test: Testfall ergänzen.

### 7. OpenAPI vs. Swagger
- **OpenAPI** ist die herstellerunabhängige Spezifikation (der formale Standard in YAML/JSON).
- **Swagger** ist die Werkzeugsammlung (von SmartBear), die diesen Standard implementiert (Swagger UI, Editor, Codegen).  
*Quelle:* OpenAPI Initiative (https://www.openapis.org/about)

### 8. Fehler im Snippet
```java
@GetMapping("/stations/add")
public String createStation(@RequestParam String name, @RequestParam double warning) {
    stationService.save(new Station(name, warning));
    return "Station successfully created!";
}
```
- GET darf keine Daten anlegen (muss POST sein).
- URL soll eine Ressource bezeichnen (`/stations`), keine Aktion (`/add`).
- Daten sollen im Request-Body (`@RequestBody`) übergeben werden, nicht als Query-Parameter.
- Rückgabe sollte Status 201 Created mit Objekt sein, kein Freitext mit Status 200.

---

## 10. Aufgetretene Probleme und Lösungen

1. **Gradle und Java 25:** Beim Start mit Java 25 gab es einen Build-Fehler. Der Gradle-Wrapper wurde auf 8.12 aktualisiert und die Java-Toolchain fest auf Version 21 eingestellt.
2. **Jackson XML-Root-Tags:** Standardmäßig waren die XML-Elemente unpassend benannt. Mit `@JacksonXmlRootElement(localName = "...")` wurden saubere Tags wie `<measurement>` und `<station>` definiert.
3. **Stabile Tests:** Die Hintergrundsimulation wurde für Tests über die Property `simulation.enabled=false` abgeschaltet, damit Tests nicht zufällig scheitern.

---

## 11. Dokumentation der KI-Prompts

Für die Erstellung und Umsetzung des Projekts wurden folgende KI-Prompts verwendet:

1. **Dokumentation:**
   - *"Schreibe mir eine ausführliche und verständliche Projektdokumentation für ein Hochwasser-Frühwarnsystem mit Spring Boot, Content Negotiation (JSON/XML) und Simulation."*
2. **Web-Frontend (`index.html`):**
   - *"Mach mir eine übersichtliche `index.html` mit CSS und jQuery, die Messwerte von einer REST-Schnittstelle lädt, per Klick zwischen JSON und XML wechseln kann, Warnstufen farblich darstellt und die Daten periodisch aktualisiert."*
3. **Recherchen zu Bibliotheken & Tests:**
   - *"Wie bindet man jackson-dataformat-xml in Spring Boot 3 mit Gradle ein und wie sorgt man dafür, dass das Root-Element @JacksonXmlRootElement heißt?"*
   - *"Wie schreibt man einen Spring Boot MockMvc Test, der den HTTP-Status 406 Not Acceptable bei ungültigem Accept-Header prüft?"*
   - *"Wie liest man mit jQuery per AJAX eine XML-Antwort aus und filtert die Werte mit $(xml).find()?"*
4. **Code-Verständnis & Problemlösung:**
   - Es wurden begleitend auch Prompts zum besseren Verständnis des Java-Codes formuliert (z. B. zur Funktionsweise von `@RestControllerAdvice` bei der Fehlerbehandlung, Scheduler-Intervallen mit `@Scheduled`, Java Stream Aggregationen für Statistiken sowie zur Behebung von Java- und Gradle-Versionskonflikten).
