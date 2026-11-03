package com.example.floodmonitor.controller;

import com.example.floodmonitor.model.Measurement;
import com.example.floodmonitor.model.Station;
import com.example.floodmonitor.model.StationStatistics;
import com.example.floodmonitor.service.StationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/stations", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
@Tag(name = "Stations", description = "Verwaltung und Abfrage von Messstationen und Messwerten")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping
    @Operation(summary = "Alle Messstationen abrufen", description = "Liefert alle Messstationen, optional gefiltert nach Fluss, Aktivitätsstatus und aktueller Warnstufe.")
    public List<Station> getStations(
            @RequestParam(required = false) String river,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String warningLevel
    ) {
        return stationService.getAllStations(river, isActive, warningLevel);
    }

    @GetMapping("/{stationId}")
    @Operation(summary = "Einzelne Messstation abrufen", description = "Gibt die Details einer spezifischen Station anhand ihrer ID zurück.")
    public Station getStationById(@PathVariable String stationId) {
        return stationService.getStationById(stationId);
    }

    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @Operation(summary = "Neue Messstation anlegen", description = "Erstellt eine neue Messstation.")
    public ResponseEntity<Station> createStation(@RequestBody Station station) {
        Station created = stationService.createStation(station);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping(value = "/{stationId}", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @Operation(summary = "Messstation aktualisieren", description = "Aktualisiert Felder einer bestehenden Station (z. B. isActive).")
    public Station updateStation(@PathVariable String stationId, @RequestBody Station station) {
        return stationService.updateStation(stationId, station);
    }

    @DeleteMapping("/{stationId}")
    @Operation(summary = "Messstation löschen", description = "Entfernt eine Messstation aus dem System.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStation(@PathVariable String stationId) {
        stationService.deleteStation(stationId);
    }

    @GetMapping("/{stationId}/measurements/latest")
    @Operation(summary = "Aktuellsten Messwert abrufen", description = "Gibt den zuletzt erfassten Messwert der Station zurück.")
    public Measurement getLatestMeasurement(@PathVariable String stationId) {
        return stationService.getLatestMeasurement(stationId);
    }

    @GetMapping("/{stationId}/measurements")
    @Operation(summary = "Historische Messwerte abrufen", description = "Gibt historische Messwerte der Station zurück, mit Filtern für Zeitraum (from, to), Warnstufe und Limit.")
    public List<Measurement> getMeasurements(
            @PathVariable String stationId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String warningLevel
    ) {
        return stationService.getMeasurements(stationId, from, to, limit, warningLevel);
    }

    @GetMapping("/{stationId}/statistics")
    @Operation(summary = "Messstatistiken einer Station", description = "Berechnet statistische Kennzahlen (Min, Max, Durchschnitt, Niederschlagssumme, Warnungsanzahl).")
    public StationStatistics getStatistics(@PathVariable String stationId) {
        return stationService.getStatistics(stationId);
    }
}
