package com.chris64233.cc.wastemanifest.manifest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ManifestApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ManifestRepository manifests;

    @Autowired
    private ManifestEventRepository events;

    @Autowired
    private ManifestVersionRepository versions;

    @Autowired
    private ManifestCorrectionRepository corrections;

    @Autowired
    private CorrectionDecisionRepository decisions;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        decisions.deleteAll();
        corrections.deleteAll();
        versions.deleteAll();
        events.deleteAll();
        manifests.deleteAll();
    }

    @Test
    void createManifestWithItemsAndQueryDetail() throws Exception {
        createManifest("M-001");

        mockMvc.perform(get("/api/manifests/M-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manifestNo").value("M-001"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.currentCustodian").value("GENERATOR"))
                .andExpect(jsonPath("$.declaredTotalWeight").value(150.0))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].wasteCategory").value("HW08"))
                .andExpect(jsonPath("$.items[1].packageCount").value(5));
    }

    @Test
    void rejectDuplicateManifestNo() throws Exception {
        createManifest("M-002");
        mockMvc.perform(post("/api/manifests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("M-002")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectNonPositiveWeight() throws Exception {
        String body = """
                {
                  "manifestNo": "M-003",
                  "generatorId": "gen-1",
                  "transporterId": "trans-1",
                  "disposerId": "disp-1",
                  "items": [{"wasteCategory": "HW08", "packageCount": 3, "declaredWeight": 0}]
                }
                """;
        mockMvc.perform(post("/api/manifests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fullHandoverFlowCompletesWithinTolerance() throws Exception {
        createManifest("M-010");

        handover("M-010", "E-1", "GENERATOR", 150, "2026-09-26T08:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.currentCustodian").value("TRANSPORTER"));

        handover("M-010", "E-2", "TRANSPORTER", 150, "2026-09-26T10:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED_BY_TRANSPORTER"))
                .andExpect(jsonPath("$.currentCustodian").value("TRANSPORTER"));

        handover("M-010", "E-3", "DISPOSER", 152, "2026-09-26T14:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.currentCustodian").value("DISPOSER"))
                .andExpect(jsonPath("$.finalWeight").value(152.0));

        mockMvc.perform(get("/api/manifests/M-010/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].type").value("GENERATOR_SHIP"))
                .andExpect(jsonPath("$[1].type").value("TRANSPORTER_RECEIVE"))
                .andExpect(jsonPath("$[2].type").value("DISPOSER_RECEIVE"));
    }

    @Test
    void rejectOutOfOrderHandover() throws Exception {
        createManifest("M-011");

        handover("M-011", "E-1", "TRANSPORTER", 150, "2026-09-26T08:00:00Z")
                .andExpect(status().is(422));
        handover("M-011", "E-2", "DISPOSER", 150, "2026-09-26T08:00:00Z")
                .andExpect(status().is(422));

        handover("M-011", "E-3", "GENERATOR", 150, "2026-09-26T08:00:00Z")
                .andExpect(status().isOk());
        handover("M-011", "E-4", "DISPOSER", 150, "2026-09-26T09:00:00Z")
                .andExpect(status().is(422));
    }

    @Test
    void rejectTimeReversal() throws Exception {
        createManifest("M-012");
        handover("M-012", "E-1", "GENERATOR", 150, "2026-09-26T08:00:00Z")
                .andExpect(status().isOk());
        handover("M-012", "E-2", "TRANSPORTER", 150, "2026-09-26T07:00:00Z")
                .andExpect(status().is(422));
    }

    @Test
    void weightDisputeRequiresMatchingConfirmation() throws Exception {
        createManifest("M-013");
        handover("M-013", "E-1", "GENERATOR", 150, "2026-09-26T08:00:00Z")
                .andExpect(status().isOk());
        handover("M-013", "E-2", "TRANSPORTER", 150, "2026-09-26T09:00:00Z")
                .andExpect(status().isOk());
        handover("M-013", "E-3", "DISPOSER", 140, "2026-09-26T10:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WEIGHT_DISPUTE"))
                .andExpect(jsonPath("$.receivedWeight").value(140.0));

        handover("M-013", "E-4", "DISPOSER", 140, "2026-09-26T11:00:00Z")
                .andExpect(status().is(422));

        confirm("M-013", "E-5", "GENERATOR", 140, "2026-09-26T11:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WEIGHT_DISPUTE"));
        confirm("M-013", "E-6", "DISPOSER", 141, "2026-09-26T12:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WEIGHT_DISPUTE"));
        confirm("M-013", "E-7", "DISPOSER", 140, "2026-09-26T13:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finalWeight").value(140.0));
    }

    @Test
    void rejectDisputeConfirmWhenNotDisputed() throws Exception {
        createManifest("M-014");
        confirm("M-014", "E-1", "GENERATOR", 150, "2026-09-26T08:00:00Z")
                .andExpect(status().is(422));
    }

    @Test
    void rejectTransporterDisputeConfirm() throws Exception {
        createManifest("M-015");
        handover("M-015", "E-1", "GENERATOR", 150, "2026-09-26T08:00:00Z");
        handover("M-015", "E-2", "TRANSPORTER", 150, "2026-09-26T09:00:00Z");
        handover("M-015", "E-3", "DISPOSER", 140, "2026-09-26T10:00:00Z");
        confirm("M-015", "E-4", "TRANSPORTER", 140, "2026-09-26T11:00:00Z")
                .andExpect(status().is(422));
    }

    @Test
    void idempotentReplayAndConflict() throws Exception {
        createManifest("M-016");
        handover("M-016", "E-1", "GENERATOR", 150, "2026-09-26T08:00:00Z")
                .andExpect(status().isOk());

        handover("M-016", "E-1", "GENERATOR", 150, "2026-09-26T08:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

        mockMvc.perform(get("/api/manifests/M-016/timeline"))
                .andExpect(jsonPath("$.length()").value(1));

        handover("M-016", "E-1", "GENERATOR", 151, "2026-09-26T08:00:00Z")
                .andExpect(status().isConflict());
        handover("M-016", "E-1", "TRANSPORTER", 150, "2026-09-26T08:00:00Z")
                .andExpect(status().isConflict());
    }

    @Test
    void disputeConfirmIdempotentReplay() throws Exception {
        createManifest("M-017");
        handover("M-017", "E-1", "GENERATOR", 150, "2026-09-26T08:00:00Z");
        handover("M-017", "E-2", "TRANSPORTER", 150, "2026-09-26T09:00:00Z");
        handover("M-017", "E-3", "DISPOSER", 140, "2026-09-26T10:00:00Z");

        confirm("M-017", "E-4", "GENERATOR", 140, "2026-09-26T11:00:00Z")
                .andExpect(status().isOk());
        confirm("M-017", "E-4", "GENERATOR", 140, "2026-09-26T11:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WEIGHT_DISPUTE"));
        confirm("M-017", "E-4", "GENERATOR", 139, "2026-09-26T11:00:00Z")
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/manifests/M-017/timeline"))
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void manifestNotFound() throws Exception {
        mockMvc.perform(get("/api/manifests/NOPE"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/manifests/NOPE/timeline"))
                .andExpect(status().isNotFound());
    }

    private void createManifest(String manifestNo) throws Exception {
        mockMvc.perform(post("/api/manifests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(manifestNo)))
                .andExpect(status().isCreated());
    }

    private String createBody(String manifestNo) {
        return """
                {
                  "manifestNo": "%s",
                  "generatorId": "gen-1",
                  "transporterId": "trans-1",
                  "disposerId": "disp-1",
                  "items": [
                    {"wasteCategory": "HW08", "packageCount": 10, "declaredWeight": 100},
                    {"wasteCategory": "HW49", "packageCount": 5, "declaredWeight": 50}
                  ]
                }
                """.formatted(manifestNo);
    }

    private org.springframework.test.web.servlet.ResultActions handover(String manifestNo, String eventNo,
                                                                        String role, double weight,
                                                                        String occurredAt) throws Exception {
        String body = """
                {"eventNo": "%s", "role": "%s", "weight": %s, "occurredAt": "%s"}
                """.formatted(eventNo, role, weight, occurredAt);
        return mockMvc.perform(post("/api/manifests/{no}/handover", manifestNo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String manifestNo, String eventNo,
                                                                       String role, double weight,
                                                                       String occurredAt) throws Exception {
        String body = """
                {"eventNo": "%s", "role": "%s", "confirmedWeight": %s, "occurredAt": "%s"}
                """.formatted(eventNo, role, weight, occurredAt);
        return mockMvc.perform(post("/api/manifests/{no}/dispute-confirmations", manifestNo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
