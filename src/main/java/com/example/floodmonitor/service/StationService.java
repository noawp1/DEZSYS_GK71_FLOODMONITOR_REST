package com.example.floodmonitor.service;

import com.example.floodmonitor.exception.BadRequestException;
import com.example.floodmonitor.exception.ResourceNotFoundException;
import com.example.floodmonitor.model.*;
import com.example.floodmonitor.repository.MeasurementRepository;
import com.example.floodmonitor.repository.StationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StationService {

    private final StationRepository stationRepository;
    private final MeasurementRepository measurementRepository;

    public StationService(StationRepository stationRepository, MeasurementRepository measurementRepository) {
        this.stationRepository = stationRepository;
        this.measurementRepository = measurementRepository;
    }

    public List<Station> getAllStations(String river, Boolean isActive, String warningLevelStr) {
        WarningLevel warningLevel = parseWarningLevel(warningLevelStr);

        return stationRepository.findAll().stream()
                .filter(s -> river == null || s.getRiver().equalsIgnoreCase(river.trim()))
                .filter(s -> isActive == null || s.getIsActive() == isActive)
                .filter(s -> {
                    if (warningLevel == null) {
                        return true;
                    }
                    Optional<Measurement> latest = measurementRepository.findLatestByStationId(s.getId());
                    WarningLevel currentLevel = latest.map(Measurement::getWarningLevel).orElse(WarningLevel.UNKNOWN);
                    return currentLevel == warningLevel;
                })
                .collect(Collectors.toList());
    }

    public Station getStationById(String id) {
        return stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station '" + id + "' wurde nicht gefunden"));
    }

    public Station createStation(Station station) {
        if (station.getId() == null || station.getId().isBlank()) {
            throw new BadRequestException("Station ID darf nicht leer sein.");
        }
        if (stationRepository.existsById(station.getId())) {
            throw new BadRequestException("Station mit ID '" + station.getId() + "' existiert bereits.");
        }
        if (station.getCriticalWaterLevel() <= station.getWarningWaterLevel()) {
            throw new BadRequestException("Der kritische Wasserstand muss größer als der Warnwasserstand sein.");
        }
        return stationRepository.save(station);
    }

    public Station updateStation(String id, Station update) {
        Station existing = getStationById(id);
        if (update.getName() != null) existing.setName(update.getName());
        if (update.getRiver() != null) existing.setRiver(update.getRiver());
        if (update.getLocation() != null) existing.setLocation(update.getLocation());
        if (update.getNormalWaterLevel() > 0) existing.setNormalWaterLevel(update.getNormalWaterLevel());
        if (update.getWarningWaterLevel() > 0) existing.setWarningWaterLevel(update.getWarningWaterLevel());
        if (update.getCriticalWaterLevel() > 0) existing.setCriticalWaterLevel(update.getCriticalWaterLevel());
        existing.setIsActive(update.getIsActive());

        if (existing.getCriticalWaterLevel() <= existing.getWarningWaterLevel()) {
            throw new BadRequestException("Der kritische Wasserstand muss größer als der Warnwasserstand sein.");
        }
        return stationRepository.save(existing);
    }

    public void deleteStation(String id) {
        if (!stationRepository.deleteById(id)) {
            throw new ResourceNotFoundException("Station '" + id + "' wurde nicht gefunden");
        }
    }

    public Measurement getLatestMeasurement(String stationId) {
        getStationById(stationId); // validates station exists
        return measurementRepository.findLatestByStationId(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Keine Messwerte für Station '" + stationId + "' vorhanden"));
    }

    public List<Measurement> getMeasurements(String stationId, String fromStr, String toStr, Integer limit, String warningLevelStr) {
        getStationById(stationId); // validates station exists

        Instant from = parseInstant(fromStr, "from");
        Instant to = parseInstant(toStr, "to");

        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("Der Parameter 'from' (" + fromStr + ") darf nicht nach 'to' (" + toStr + ") liegen.");
        }

        if (limit != null && (limit <= 0 || limit > 1000)) {
            throw new BadRequestException("Der Parameter 'limit' muss zwischen 1 und 1000 liegen.");
        }

        WarningLevel warningLevel = parseWarningLevel(warningLevelStr);
        int finalLimit = (limit != null) ? limit : 100;

        return measurementRepository.findByStationId(stationId, from, to, warningLevel, finalLimit);
    }

    public List<Alert> getAlerts(String river, String warningLevelStr) {
        WarningLevel filterLevel = parseWarningLevel(warningLevelStr);

        return stationRepository.findAll().stream()
                .filter(s -> river == null || s.getRiver().equalsIgnoreCase(river.trim()))
                .map(s -> {
                    Optional<Measurement> latestOpt = measurementRepository.findLatestByStationId(s.getId());
                    if (latestOpt.isEmpty()) {
                        return null;
                    }
                    Measurement m = latestOpt.get();
                    WarningLevel level = m.getWarningLevel();

                    // If a specific filter is set, match it.
                    // If no filter is set, show active warnings (WARNING or CRITICAL)
                    if (filterLevel != null) {
                        if (level != filterLevel) return null;
                    } else {
                        if (level != WarningLevel.WARNING && level != WarningLevel.CRITICAL) {
                            return null;
                        }
                    }

                    String msg = "Wasserstand " + m.getWaterLevel() + " cm (Warnwert: "
                            + s.getWarningWaterLevel() + " cm, Kritisch: " + s.getCriticalWaterLevel() + " cm)";
                    return new Alert(s.getId(), s.getName(), s.getRiver(), m.getTimestamp(), m.getWaterLevel(), level, msg);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public StationStatistics getStatistics(String stationId) {
        Station station = getStationById(stationId);
        List<Measurement> list = measurementRepository.findAllByStationId(station.getId());

        if (list.isEmpty()) {
            return new StationStatistics(station.getId(), 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0);
        }

        double minWater = list.stream().mapToDouble(Measurement::getWaterLevel).min().orElse(0.0);
        double maxWater = list.stream().mapToDouble(Measurement::getWaterLevel).max().orElse(0.0);
        double avgWater = list.stream().mapToDouble(Measurement::getWaterLevel).average().orElse(0.0);
        double avgFlow = list.stream().mapToDouble(Measurement::getFlowRate).average().orElse(0.0);
        double totalRain = list.stream().mapToDouble(Measurement::getRainfall).sum();
        long warningCount = list.stream().filter(m -> m.getWarningLevel() == WarningLevel.WARNING).count();
        long criticalCount = list.stream().filter(m -> m.getWarningLevel() == WarningLevel.CRITICAL).count();

        // Rounding to 1 decimal place
        avgWater = Math.round(avgWater * 10.0) / 10.0;
        avgFlow = Math.round(avgFlow * 10.0) / 10.0;
        totalRain = Math.round(totalRain * 10.0) / 10.0;

        return new StationStatistics(station.getId(), minWater, maxWater, avgWater, avgFlow, totalRain,
                list.size(), warningCount, criticalCount);
    }

    private Instant parseInstant(String text, String paramName) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Ungültiges Datumsformat für '" + paramName + "': '" + text
                    + "'. Erwartet wird ISO-8601 (z.B. 2026-05-01T08:00:00Z).");
        }
    }

    private WarningLevel parseWarningLevel(String str) {
        if (str == null || str.isBlank()) {
            return null;
        }
        try {
            return WarningLevel.valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Ungültige Warnstufe: '" + str + "'. Erlaubt sind: NORMAL, WARNING, CRITICAL, UNKNOWN");
        }
    }
}

