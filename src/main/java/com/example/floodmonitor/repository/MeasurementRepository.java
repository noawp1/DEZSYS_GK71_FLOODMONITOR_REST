package com.example.floodmonitor.repository;

import com.example.floodmonitor.model.Measurement;
import com.example.floodmonitor.model.WarningLevel;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Repository
public class MeasurementRepository {

    private final Map<String, List<Measurement>> measurementsByStation = new ConcurrentHashMap<>();

    public synchronized void save(Measurement measurement) {
        measurementsByStation
                .computeIfAbsent(measurement.getStationId(), k -> new CopyOnWriteArrayList<>())
                .add(measurement);
    }

    public Optional<Measurement> findLatestByStationId(String stationId) {
        List<Measurement> list = measurementsByStation.get(stationId);
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.get(list.size() - 1));
    }

    public List<Measurement> findAllByStationId(String stationId) {
        List<Measurement> list = measurementsByStation.get(stationId);
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }

    public List<Measurement> findByStationId(String stationId, Instant from, Instant to, WarningLevel warningLevel, int limit) {
        List<Measurement> list = measurementsByStation.get(stationId);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        return list.stream()
                .filter(m -> from == null || !m.getTimestamp().isBefore(from))
                .filter(m -> to == null || !m.getTimestamp().isAfter(to))
                .filter(m -> warningLevel == null || m.getWarningLevel() == warningLevel)
                .sorted(Comparator.comparing(Measurement::getTimestamp).reversed())
                .limit(limit > 0 ? limit : 100)
                .collect(Collectors.toList());
    }

    public void clear() {
        measurementsByStation.clear();
    }
}

