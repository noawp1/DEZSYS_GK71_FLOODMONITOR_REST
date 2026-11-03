package com.example.floodmonitor;

import com.example.floodmonitor.model.*;
import com.example.floodmonitor.repository.MeasurementRepository;
import com.example.floodmonitor.repository.StationRepository;
import com.example.floodmonitor.service.SimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "simulation.enabled=false"
})
class FloodMonitorApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private MeasurementRepository measurementRepository;

    @Autowired
    private SimulationService simulationService;

    private Station testStation;

    @BeforeEach
    void setUp() {
        testStation = stationRepository.findById("ST-001").orElseGet(() ->
                stationRepository.save(new Station("ST-001", "Station Wien", "Donau",
                        new Location(48.2, 16.4), 260.0, 500.0, 700.0, true))
        );
    }

    @Test
    @DisplayName("1. Abruf aller Stationen als JSON")
    void test1_getAllStationsAsJson() throws Exception {
        mockMvc.perform(get("/api/v1/stations")
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    @DisplayName("2. Abruf aller Stationen als XML")
    void test2_getAllStationsAsXml() throws Exception {
        mockMvc.perform(get("/api/v1/stations")
                        .header("Accept", MediaType.APPLICATION_XML_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("<id>ST-001</id>")));
    }

    @Test
    @DisplayName("3. Abruf einer existierenden Station")
    void test3_getExistingStation() throws Exception {
        mockMvc.perform(get("/api/v1/stations/ST-001")
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ST-001"))
                .andExpect(jsonPath("$.river").value("Donau"));
    }

    @Test
    @DisplayName("4. Abruf einer unbekannten Station (404 Not Found)")
    void test4_getUnknownStation() throws Exception {
        mockMvc.perform(get("/api/v1/stations/ST-999")
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message", containsString("ST-999")));
    }

    @Test
    @DisplayName("5. Abruf des aktuellsten Messwerts")
    void test5_getLatestMeasurement() throws Exception {
        // Save a known measurement
        Measurement m = new Measurement("ST-001", Instant.now(), 275.5, 620.0, 0.0, 16.0, 92,
                StationStatus.ONLINE, WarningLevel.NORMAL);
        measurementRepository.save(m);

        mockMvc.perform(get("/api/v1/stations/ST-001/measurements/latest")
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationId").value("ST-001"))
                .andExpect(jsonPath("$.waterLevel").value(275.5))
                .andExpect(jsonPath("$.warningLevel").value("NORMAL"));
    }

    @Test
    @DisplayName("6. Filterung nach Zeitraum")
    void test6_filterByTimeRange() throws Exception {
        Instant baseTime = Instant.parse("2026-05-01T10:00:00Z");
        Measurement m1 = new Measurement("ST-001", baseTime, 250.0, 500.0, 0.0, 15.0, 90,
                StationStatus.ONLINE, WarningLevel.NORMAL);
        Measurement m2 = new Measurement("ST-001", baseTime.plus(2, ChronoUnit.HOURS), 280.0, 550.0, 5.0, 15.0, 89,
                StationStatus.ONLINE, WarningLevel.NORMAL);
        Measurement m3 = new Measurement("ST-001", baseTime.plus(5, ChronoUnit.HOURS), 310.0, 600.0, 10.0, 15.0, 88,
                StationStatus.ONLINE, WarningLevel.NORMAL);

        measurementRepository.save(m1);
        measurementRepository.save(m2);
        measurementRepository.save(m3);

        String from = "2026-05-01T11:00:00Z";
        String to = "2026-05-01T13:00:00Z";

        mockMvc.perform(get("/api/v1/stations/ST-001/measurements")
                        .param("from", from)
                        .param("to", to)
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].waterLevel").value(280.0));
    }

    @Test
    @DisplayName("7. Ungültiger Zeitraum (from nach to führt zu 400 Bad Request)")
    void test7_invalidTimeRange() throws Exception {
        mockMvc.perform(get("/api/v1/stations/ST-001/measurements")
                        .param("from", "2026-05-02T12:00:00Z")
                        .param("to", "2026-05-01T12:00:00Z")
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("darf nicht nach")));
    }

    @Test
    @DisplayName("8. Warnstufe NORMAL")
    void test8_warningLevelNormal() {
        // normal: 260, warning: 500, critical: 700
        WarningLevel level = simulationService.calculateWarningLevel(testStation, 350.0, StationStatus.ONLINE);
        assertEquals(WarningLevel.NORMAL, level);
    }

    @Test
    @DisplayName("9. Warnstufe WARNING")
    void test9_warningLevelWarning() {
        // Wasserstand ab Warnwert (500)
        WarningLevel level = simulationService.calculateWarningLevel(testStation, 500.0, StationStatus.ONLINE);
        assertEquals(WarningLevel.WARNING, level);

        WarningLevel levelHigher = simulationService.calculateWarningLevel(testStation, 650.0, StationStatus.ONLINE);
        assertEquals(WarningLevel.WARNING, levelHigher);
    }

    @Test
    @DisplayName("10. Warnstufe CRITICAL")
    void test10_warningLevelCritical() {
        // Wasserstand ab kritischem Wert (700)
        WarningLevel level = simulationService.calculateWarningLevel(testStation, 700.0, StationStatus.ONLINE);
        assertEquals(WarningLevel.CRITICAL, level);

        WarningLevel levelHigher = simulationService.calculateWarningLevel(testStation, 850.0, StationStatus.ONLINE);
        assertEquals(WarningLevel.CRITICAL, levelHigher);
    }

    @Test
    @DisplayName("10b. Warnstufe UNKNOWN bei nicht betriebsbereiter Station")
    void test10b_warningLevelUnknown() {
        WarningLevel level = simulationService.calculateWarningLevel(testStation, 200.0, StationStatus.MAINTENANCE);
        assertEquals(WarningLevel.UNKNOWN, level);

        WarningLevel levelOffline = simulationService.calculateWarningLevel(testStation, 200.0, StationStatus.OFFLINE);
        assertEquals(WarningLevel.UNKNOWN, levelOffline);
    }

    @Test
    @DisplayName("11. Nicht unterstütztes Format (406 Not Acceptable)")
    void test11_unsupportedFormat() throws Exception {
        mockMvc.perform(get("/api/v1/stations")
                        .header("Accept", "image/png"))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    @DisplayName("12. Maximales Limit (Limit > 1000 oder <= 0 führt zu 400 Bad Request)")
    void test12_maxLimit() throws Exception {
        mockMvc.perform(get("/api/v1/stations/ST-001/measurements")
                        .param("limit", "1500")
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("limit")));

        mockMvc.perform(get("/api/v1/stations/ST-001/measurements")
                        .param("limit", "-5")
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Bonus: Abruf von Alerts")
    void testBonus_alerts() throws Exception {
        // Insert a critical measurement
        Measurement critical = new Measurement("ST-001", Instant.now(), 750.0, 1600.0, 25.0, 14.0, 85,
                StationStatus.ONLINE, WarningLevel.CRITICAL);
        measurementRepository.save(critical);

        mockMvc.perform(get("/api/v1/alerts")
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].warningLevel").value("CRITICAL"));
    }

    @Test
    @DisplayName("Bonus: Abruf von Stations-Statistiken")
    void testBonus_statistics() throws Exception {
        mockMvc.perform(get("/api/v1/stations/ST-001/statistics")
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationId").value("ST-001"))
                .andExpect(jsonPath("$.measurementCount").isNumber())
                .andExpect(jsonPath("$.avgWaterLevel").isNumber());
    }
}

