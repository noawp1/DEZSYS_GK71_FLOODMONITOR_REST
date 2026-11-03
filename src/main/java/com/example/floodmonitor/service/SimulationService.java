package com.example.floodmonitor.service;

import com.example.floodmonitor.model.*;
import com.example.floodmonitor.repository.MeasurementRepository;
import com.example.floodmonitor.repository.StationRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class SimulationService {

    private final StationRepository stationRepository;
    private final MeasurementRepository measurementRepository;
    private final Random random = new Random();

    @Value("${simulation.enabled:true}")
    private boolean enabled;

    public SimulationService(StationRepository stationRepository, MeasurementRepository measurementRepository) {
        this.stationRepository = stationRepository;
        this.measurementRepository = measurementRepository;
    }

    @PostConstruct
    public void initInitialData() {
        // Pre-populate realistic historical measurements (e.g. 5 historical data points)
        Instant now = Instant.now();
        List<Station> stations = stationRepository.findAll();

        for (Station station : stations) {
            double currentWater = station.getNormalWaterLevel();
            int battery = 95;
            for (int i = 5; i >= 0; i--) {
                Instant time = now.minus(i * 10L, ChronoUnit.MINUTES);
                double rainfall = Math.round(random.nextDouble() * 15.0 * 10.0) / 10.0;
                // Water level change based on rainfall
                double delta = (rainfall - 3.0) * 0.7 + (random.nextDouble() * 4.0 - 2.0);
                currentWater = Math.max(10.0, Math.round((currentWater + delta) * 10.0) / 10.0);
                double flowRate = Math.round((currentWater * 2.2 + random.nextDouble() * 10.0) * 10.0) / 10.0;
                double temp = Math.round((12.0 + random.nextDouble() * 6.0) * 10.0) / 10.0;
                battery = Math.max(10, battery - random.nextInt(2));

                StationStatus status = battery < 15 ? StationStatus.MAINTENANCE : StationStatus.ONLINE;
                WarningLevel warningLevel = calculateWarningLevel(station, currentWater, status);

                measurementRepository.save(new Measurement(
                        station.getId(),
                        time,
                        currentWater,
                        flowRate,
                        rainfall,
                        temp,
                        battery,
                        status,
                        warningLevel
                ));
            }
        }
    }

    @Scheduled(fixedRateString = "${simulation.interval:10000}")
    public void runScheduledSimulation() {
        if (!enabled) {
            return;
        }
        simulateStep();
    }

    public void simulateStep() {
        Instant now = Instant.now();
        List<Station> stations = stationRepository.findAll();

        for (Station station : stations) {
            // Regel: Für deaktivierte oder ausgefallene Stationen werden keine neuen Messungen erzeugt
            if (!station.getIsActive()) {
                continue;
            }

            Optional<Measurement> prevOpt = measurementRepository.findLatestByStationId(station.getId());
            Measurement prev = prevOpt.orElse(null);

            if (prev != null && prev.getStatus() == StationStatus.OFFLINE) {
                continue;
            }

            Measurement next = generateNextMeasurement(station, prev, now);
            measurementRepository.save(next);
        }
    }

    public Measurement generateNextMeasurement(Station station, Measurement prev, Instant timestamp) {
        double prevWater = (prev != null) ? prev.getWaterLevel() : station.getNormalWaterLevel();
        int prevBattery = (prev != null) ? prev.getBatteryLevel() : 90;
        double prevTemp = (prev != null) ? prev.getTemperature() : 15.0;

        // 1. Niederschlag: Meist moderat (0-15 mm/h), gelegentlich stark
        double rainfall = Math.round((random.nextDouble() < 0.8 ? random.nextDouble() * 8.0 : random.nextDouble() * 30.0) * 10.0) / 10.0;

        // 2. Wasserstand: Starker Regen erhöht Wasserstand, bei wenig Regen sinkt er Richtung normalWaterLevel
        double rainImpact = (rainfall - 4.0) * 0.8;
        double drift = (random.nextDouble() * 4.0 - 2.0); // sanfte Schwankung (-2 bis +2 cm)
        double nextWater = Math.max(0.0, Math.round((prevWater + rainImpact + drift) * 10.0) / 10.0);

        // 3. Durchflussmenge: Höherer Wasserstand -> höhere Durchflussmenge
        double nextFlow = Math.max(0.0, Math.round((nextWater * 2.3 + (random.nextDouble() * 8.0 - 4.0)) * 10.0) / 10.0);

        // 4. Temperatur: Sanfte Veränderung
        double nextTemp = Math.round(Math.max(-5.0, Math.min(35.0, prevTemp + (random.nextDouble() * 0.8 - 0.4))) * 10.0) / 10.0;

        // 5. Akku: Leicht sinkend (0-1%), gelegentliche Ladung durch Solar
        int batteryDelta = random.nextDouble() < 0.15 ? 1 : (random.nextDouble() < 0.4 ? -1 : 0);
        int nextBattery = Math.max(5, Math.min(100, prevBattery + batteryDelta));

        // 6. Status: Bei sehr niedrigem Akkustand (< 15%) wechselt der Status auf MAINTENANCE
        StationStatus status = nextBattery < 15 ? StationStatus.MAINTENANCE : StationStatus.ONLINE;

        // 7. Warnstufe berechnen
        WarningLevel warningLevel = calculateWarningLevel(station, nextWater, status);

        return new Measurement(
                station.getId(),
                timestamp,
                nextWater,
                nextFlow,
                rainfall,
                nextTemp,
                nextBattery,
                status,
                warningLevel
        );
    }

    public WarningLevel calculateWarningLevel(Station station, double waterLevel, StationStatus status) {
        if (status != StationStatus.ONLINE) {
            return WarningLevel.UNKNOWN;
        }
        if (waterLevel >= station.getCriticalWaterLevel()) {
            return WarningLevel.CRITICAL;
        }
        if (waterLevel >= station.getWarningWaterLevel()) {
            return WarningLevel.WARNING;
        }
        return WarningLevel.NORMAL;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
