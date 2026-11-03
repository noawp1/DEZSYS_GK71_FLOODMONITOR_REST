package com.example.floodmonitor.repository;

import com.example.floodmonitor.model.Location;
import com.example.floodmonitor.model.Station;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class StationRepository {

    private final Map<String, Station> stations = new ConcurrentHashMap<>();

    public StationRepository() {
        initDefaultStations();
    }

    private void initDefaultStations() {
        save(new Station("ST-001", "Station Wien - Reichsbrücke", "Donau",
                new Location(48.2272, 16.4093), 260.0, 500.0, 700.0, true));
        save(new Station("ST-002", "Station Linz - Donaupromenade", "Donau",
                new Location(48.3064, 14.2858), 310.0, 550.0, 750.0, true));
        save(new Station("ST-003", "Station Salzburg - Staatsbrücke", "Salzach",
                new Location(47.8005, 13.0444), 190.0, 380.0, 520.0, true));
        save(new Station("ST-004", "Station Innsbruck - Innbrücke", "Inn",
                new Location(47.2692, 11.3933), 160.0, 320.0, 450.0, true));
    }

    public List<Station> findAll() {
        return new ArrayList<>(stations.values());
    }

    public Optional<Station> findById(String id) {
        return Optional.ofNullable(stations.get(id));
    }

    public Station save(Station station) {
        stations.put(station.getId(), station);
        return station;
    }

    public boolean deleteById(String id) {
        return stations.remove(id) != null;
    }

    public boolean existsById(String id) {
        return stations.containsKey(id);
    }
}

