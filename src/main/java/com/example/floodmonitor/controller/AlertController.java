package com.example.floodmonitor.controller;

import com.example.floodmonitor.model.Alert;
import com.example.floodmonitor.service.StationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/alerts", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
@Tag(name = "Alerts", description = "Abfrage von Hochwasserwarnungen")
public class AlertController {

    private final StationService stationService;

    public AlertController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping
    @Operation(summary = "Aktuelle Warnungen abrufen", description = "Liefert aktive Hochwasserwarnungen (WARNING, CRITICAL), optional filterbar nach Fluss und Warnstufe.")
    public List<Alert> getAlerts(
            @RequestParam(required = false) String river,
            @RequestParam(required = false) String warningLevel
    ) {
        return stationService.getAlerts(river, warningLevel);
    }
}

