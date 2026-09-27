package com.chris64233.cc.wastemanifest.manifest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CorrectionApiIntegrationTest {

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
    void weightCorrectionAppliesAtomicallyAfterRequiredApprovals() throws Exception {
        createManifest("R-001");
        completeManifest("R-001");

        createCorrection("R-001", "CR-1", weightChange(0, "140"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.baseVersionNo").value(1))
                .andExpect(jsonPath("$.reason").value("申报重量填写错误"))
                .andExpect(jsonPath("$.evidence").value("称重单 WS-1001"))
                .andExpect(jsonPath("$.disputeImpact").value("DISPUTE_WOULD_HAVE_OCCURRED"))
                .andExpect(jsonPath("$.requiredApprovals.length()").value(2))
                .andExpect(jsonPath("$.requiredApprovals[0]").value("GENERATOR"))
                .andExpect(jsonPath("$.requiredApprovals[1]").value("DISPOSER"))
                .andExpect(jsonPath("$.changes[0].oldValue").value("100.000"))
                .andExpect(jsonPath("$.changes[0].newValue").value("140.000"));

        decide("R-001", "CR-1", "CD-1", "GENERATOR", "APPROVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/manifests/R-001"))
                .andExpect(jsonPath("$.currentVersionNo").value(1))
                .andExpect(jsonPath("$.declaredTotalWeight").value(150.0));

        decide("R-001", "CR-1", "CD-2", "DISPOSER", "APPROVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.resultVersionNo").value(2))
                .andExpect(jsonPath("$.decisions.length()").value(2));

        mockMvc.perform(get("/api/manifests/R-001"))
                .andExpect(jsonPath("$.currentVersionNo").value(2))
                .andExpect(jsonPath("$.declaredTotalWeight").value(190.0))
                .andExpect(jsonPath("$.items[0].declaredWeight").value(140.0))
                .andExpect(jsonPath("$.items[1].declaredWeight").value(50.0))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/manifests/R-001/revisions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersionNo").value(2))
                .andExpect(jsonPath("$.versions.length()").value(2))
                .andExpect(jsonPath("$.versions[0].source").value("ORIGINAL"))
                .andExpect(jsonPath("$.versions[0].declaredTotalWeight").value(150.0))
                .andExpect(jsonPath("$.versions[1].source").value("CORRECTION"))
                .andExpect(jsonPath("$.versions[1].correctionNo").value("CR-1"))
                .andExpect(jsonPath("$.versions[1].declaredTotalWeight").value(190.0))
                .andExpect(jsonPath("$.versions[1].diffFromPrevious.length()").value(1))
                .andExpect(jsonPath("$.versions[1].diffFromPrevious[0].field").value("WEIGHT"))
                .andExpect(jsonPath("$.versions[1].diffFromPrevious[0].oldValue").value("100.000"))
                .andExpect(jsonPath("$.versions[1].diffFromPrevious[0].newValue").value("140.000"))
                .andExpect(jsonPath("$.corrections.length()").value(1))
                .andExpect(jsonPath("$.corrections[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.corrections[0].decisions.length()").value(2))
                .andExpect(jsonPath("$.corrections[0].decisions[0].role").value("GENERATOR"))
                .andExpect(jsonPath("$.corrections[0].decisions[1].role").value("DISPOSER"));

        mockMvc.perform(get("/api/manifests/R-001/timeline"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void wasteCategoryChangeRequiresRegulatorReview() throws Exception {
        createManifest("R-002");
        completeManifest("R-002");

        createCorrection("R-002", "CR-1", categoryChange(0, "HW09"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disputeImpact").value("CONCLUSION_UNCHANGED"))
                .andExpect(jsonPath("$.requiredApprovals.length()").value(3))
                .andExpect(jsonPath("$.requiredApprovals[2]").value("REGULATOR"));

        decide("R-002", "CR-1", "CD-1", "TRANSPORTER", "APPROVE")
                .andExpect(status().is(422));

        decide("R-002", "CR-1", "CD-2", "GENERATOR", "APPROVE")
                .andExpect(jsonPath("$.status").value("PENDING"));
        decide("R-002", "CR-1", "CD-3", "DISPOSER", "APPROVE")
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/manifests/R-002"))
                .andExpect(jsonPath("$.currentVersionNo").value(1));

        decide("R-002", "CR-1", "CD-4", "REGULATOR", "APPROVE")
                .andExpect(jsonPath("$.status").value("APPLIED"));

        mockMvc.perform(get("/api/manifests/R-002"))
                .andExpect(jsonPath("$.currentVersionNo").value(2))
                .andExpect(jsonPath("$.items[0].wasteCategory").value("HW09"));
    }

    @Test
    void packageCountChangeRequiresGeneratorAndTransporter() throws Exception {
        createManifest("R-003");
        completeManifest("R-003");

        createCorrection("R-003", "CR-1", packageCountChange(1, "8"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiredApprovals.length()").value(2))
                .andExpect(jsonPath("$.requiredApprovals[0]").value("GENERATOR"))
                .andExpect(jsonPath("$.requiredApprovals[1]").value("TRANSPORTER"));

        decide("R-003", "CR-1", "CD-1", "DISPOSER", "APPROVE")
                .andExpect(status().is(422));
        decide("R-003", "CR-1", "CD-2", "GENERATOR", "APPROVE")
                .andExpect(jsonPath("$.status").value("PENDING"));
        decide("R-003", "CR-1", "CD-3", "TRANSPORTER", "APPROVE")
                .andExpect(jsonPath("$.status").value("APPLIED"));

        mockMvc.perform(get("/api/manifests/R-003"))
                .andExpect(jsonPath("$.items[1].packageCount").value(8))
                .andExpect(jsonPath("$.declaredTotalWeight").value(150.0));
    }

    @Test
    void rejectCorrectionOnNonCompletedManifest() throws Exception {
        createManifest("R-004");
        createCorrection("R-004", "CR-1", weightChange(0, "140"))
                .andExpect(status().is(422));
    }

    @Test
    void onlyOneActiveCorrectionAllowed() throws Exception {
        createManifest("R-005");
        completeManifest("R-005");

        createCorrection("R-005", "CR-1", weightChange(0, "140"))
                .andExpect(status().isCreated());
        createCorrection("R-005", "CR-2", weightChange(0, "145"))
                .andExpect(status().isConflict());

        decide("R-005", "CR-1", "CD-1", "GENERATOR", "REJECT")
                .andExpect(jsonPath("$.status").value("REJECTED"));

        createCorrection("R-005", "CR-2", weightChange(0, "145"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void correctionIdempotentReplayAndConflict() throws Exception {
        createManifest("R-006");
        completeManifest("R-006");

        createCorrection("R-006", "CR-1", weightChange(0, "140"))
                .andExpect(status().isCreated());
        createCorrection("R-006", "CR-1", weightChange(0, "140"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/manifests/R-006/corrections/CR-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctionNo").value("CR-1"));

        String differentReason = """
                {"correctionNo": "CR-1", "reason": "另一个理由", "evidence": "称重单 WS-1001",
                 "changes": [{"itemIndex": 0, "field": "WEIGHT", "newValue": "140"}]}
                """;
        mockMvc.perform(post("/api/manifests/R-006/corrections")
                        .contentType(MediaType.APPLICATION_JSON).content(differentReason))
                .andExpect(status().isConflict());

        createCorrection("R-006", "CR-1", weightChange(0, "141"))
                .andExpect(status().isConflict());
    }

    @Test
    void decisionIdempotentReplayAndConflict() throws Exception {
        createManifest("R-007");
        completeManifest("R-007");
        createCorrection("R-007", "CR-1", weightChange(0, "140"))
                .andExpect(status().isCreated());

        decide("R-007", "CR-1", "CD-1", "GENERATOR", "APPROVE")
                .andExpect(status().isOk());
        decide("R-007", "CR-1", "CD-1", "GENERATOR", "APPROVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisions.length()").value(1));
        decide("R-007", "CR-1", "CD-1", "GENERATOR", "REJECT")
                .andExpect(status().isConflict());
        decide("R-007", "CR-1", "CD-1", "DISPOSER", "APPROVE")
                .andExpect(status().isConflict());
    }

    @Test
    void rejectDecisionTerminatesCorrection() throws Exception {
        createManifest("R-008");
        completeManifest("R-008");
        createCorrection("R-008", "CR-1", weightChange(0, "140"))
                .andExpect(status().isCreated());

        decide("R-008", "CR-1", "CD-1", "GENERATOR", "REJECT")
                .andExpect(jsonPath("$.status").value("REJECTED"));
        decide("R-008", "CR-1", "CD-2", "DISPOSER", "APPROVE")
                .andExpect(status().is(422));

        mockMvc.perform(get("/api/manifests/R-008"))
                .andExpect(jsonPath("$.currentVersionNo").value(1));
    }

    @Test
    void withdrawInvalidatesCorrection() throws Exception {
        createManifest("R-009");
        completeManifest("R-009");
        createCorrection("R-009", "CR-1", weightChange(0, "140"))
                .andExpect(status().isCreated());

        decide("R-009", "CR-1", "CD-1", "GENERATOR", "APPROVE")
                .andExpect(jsonPath("$.status").value("PENDING"));
        decide("R-009", "CR-1", "CD-2", "GENERATOR", "WITHDRAW")
                .andExpect(jsonPath("$.status").value("INVALIDATED"));
        decide("R-009", "CR-1", "CD-3", "DISPOSER", "APPROVE")
                .andExpect(status().is(422));

        mockMvc.perform(get("/api/manifests/R-009"))
                .andExpect(jsonPath("$.currentVersionNo").value(1));
    }

    @Test
    void freezeInvalidatesPendingCorrectionAndBlocksOperations() throws Exception {
        createManifest("R-010");
        completeManifest("R-010");
        createCorrection("R-010", "CR-1", weightChange(0, "140"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/manifests/R-010/freeze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frozen").value(true));

        mockMvc.perform(get("/api/manifests/R-010/corrections/CR-1"))
                .andExpect(jsonPath("$.status").value("INVALIDATED"));

        createCorrection("R-010", "CR-2", weightChange(0, "145"))
                .andExpect(status().is(422));
        decide("R-010", "CR-1", "CD-1", "GENERATOR", "APPROVE")
                .andExpect(status().is(422));

        mockMvc.perform(post("/api/manifests/R-010/unfreeze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frozen").value(false));

        createCorrection("R-010", "CR-2", weightChange(0, "145"))
                .andExpect(status().isCreated());
    }

    @Test
    void freezeBlocksHandover() throws Exception {
        createManifest("R-011");
        mockMvc.perform(post("/api/manifests/R-011/freeze"))
                .andExpect(status().isOk());
        handover("R-011", "E-1", "GENERATOR", 150, "2026-09-26T08:00:00Z")
                .andExpect(status().is(422));
        mockMvc.perform(post("/api/manifests/R-011/unfreeze"))
                .andExpect(status().isOk());
        handover("R-011", "E-1", "GENERATOR", 150, "2026-09-26T08:00:00Z")
                .andExpect(status().isOk());
    }

    @Test
    void disputeImpactReflectsOriginalDisputeConclusion() throws Exception {
        createManifest("R-012");
        handover("R-012", "E-1", "GENERATOR", 150, "2026-09-26T08:00:00Z")
                .andExpect(status().isOk());
        handover("R-012", "E-2", "TRANSPORTER", 150, "2026-09-26T09:00:00Z")
                .andExpect(status().isOk());
        handover("R-012", "E-3", "DISPOSER", 140, "2026-09-26T10:00:00Z")
                .andExpect(jsonPath("$.status").value("WEIGHT_DISPUTE"));
        confirm("R-012", "E-4", "GENERATOR", 140, "2026-09-26T11:00:00Z")
                .andExpect(status().isOk());
        confirm("R-012", "E-5", "DISPOSER", 140, "2026-09-26T12:00:00Z")
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        createCorrection("R-012", "CR-1", weightChange(0, "90"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disputeImpact").value("DISPUTE_WOULD_NOT_HAVE_OCCURRED"));

        decide("R-012", "CR-1", "CD-1", "GENERATOR", "APPROVE").andExpect(status().isOk());
        decide("R-012", "CR-1", "CD-2", "DISPOSER", "APPROVE")
                .andExpect(jsonPath("$.status").value("APPLIED"));

        mockMvc.perform(get("/api/manifests/R-012"))
                .andExpect(jsonPath("$.declaredTotalWeight").value(140.0))
                .andExpect(jsonPath("$.receivedWeight").value(140.0))
                .andExpect(jsonPath("$.finalWeight").value(140.0));

        createCorrection("R-012", "CR-2", weightChange(1, "40"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.baseVersionNo").value(2))
                .andExpect(jsonPath("$.disputeImpact").value("DISPUTE_WOULD_REMAIN"));
    }

    @Test
    void originalVersionAndEventsStayIntactAfterCorrection() throws Exception {
        createManifest("R-013");
        completeManifest("R-013");
        createCorrection("R-013", "CR-1", weightChange(0, "140") + "," + packageCountChange(1, "8"))
                .andExpect(status().isCreated());
        decide("R-013", "CR-1", "CD-1", "GENERATOR", "APPROVE").andExpect(status().isOk());
        decide("R-013", "CR-1", "CD-2", "TRANSPORTER", "APPROVE").andExpect(status().isOk());
        decide("R-013", "CR-1", "CD-3", "DISPOSER", "APPROVE")
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.requiredApprovals.length()").value(3));

        mockMvc.perform(get("/api/manifests/R-013/revisions"))
                .andExpect(jsonPath("$.versions[0].items[0].declaredWeight").value(100.0))
                .andExpect(jsonPath("$.versions[0].items[1].packageCount").value(5))
                .andExpect(jsonPath("$.versions[1].items[0].declaredWeight").value(140.0))
                .andExpect(jsonPath("$.versions[1].items[1].packageCount").value(8))
                .andExpect(jsonPath("$.versions[1].diffFromPrevious.length()").value(2));

        mockMvc.perform(get("/api/manifests/R-013/timeline"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].type").value("GENERATOR_SHIP"))
                .andExpect(jsonPath("$[2].type").value("DISPOSER_RECEIVE"));
    }

    @Test
    void correctionNotFound() throws Exception {
        createManifest("R-014");
        completeManifest("R-014");
        mockMvc.perform(get("/api/manifests/R-014/corrections/NOPE"))
                .andExpect(status().isNotFound());
        decide("R-014", "NOPE", "CD-1", "GENERATOR", "APPROVE")
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/manifests/NOPE/corrections/CR-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidChangesRejected() throws Exception {
        createManifest("R-015");
        completeManifest("R-015");

        createCorrection("R-015", "CR-1", weightChange(5, "140"))
                .andExpect(status().is(422));
        createCorrection("R-015", "CR-1", weightChange(0, "100"))
                .andExpect(status().is(422));
        createCorrection("R-015", "CR-1", weightChange(0, "abc"))
                .andExpect(status().is(422));
        createCorrection("R-015", "CR-1", weightChange(0, "10.0001"))
                .andExpect(status().is(422));
        createCorrection("R-015", "CR-1", packageCountChange(0, "0"))
                .andExpect(status().is(422));
        createCorrection("R-015", "CR-1", weightChange(0, "140") + "," + weightChange(0, "141"))
                .andExpect(status().is(422));
    }

    private void createManifest(String manifestNo) throws Exception {
        String body = """
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
        mockMvc.perform(post("/api/manifests")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private void completeManifest(String manifestNo) throws Exception {
        handover(manifestNo, "E-1", "GENERATOR", 150, "2026-09-26T08:00:00Z")
                .andExpect(status().isOk());
        handover(manifestNo, "E-2", "TRANSPORTER", 150, "2026-09-26T09:00:00Z")
                .andExpect(status().isOk());
        handover(manifestNo, "E-3", "DISPOSER", 150, "2026-09-26T10:00:00Z")
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    private ResultActions handover(String manifestNo, String eventNo, String role, double weight,
                                   String occurredAt) throws Exception {
        String body = """
                {"eventNo": "%s", "role": "%s", "weight": %s, "occurredAt": "%s"}
                """.formatted(eventNo, role, weight, occurredAt);
        return mockMvc.perform(post("/api/manifests/{no}/handover", manifestNo)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions confirm(String manifestNo, String eventNo, String role, double weight,
                                  String occurredAt) throws Exception {
        String body = """
                {"eventNo": "%s", "role": "%s", "confirmedWeight": %s, "occurredAt": "%s"}
                """.formatted(eventNo, role, weight, occurredAt);
        return mockMvc.perform(post("/api/manifests/{no}/dispute-confirmations", manifestNo)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions createCorrection(String manifestNo, String correctionNo,
                                           String changesJson) throws Exception {
        String body = """
                {"correctionNo": "%s", "reason": "申报重量填写错误", "evidence": "称重单 WS-1001",
                 "changes": [%s]}
                """.formatted(correctionNo, changesJson);
        return mockMvc.perform(post("/api/manifests/{no}/corrections", manifestNo)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions decide(String manifestNo, String correctionNo, String decisionNo,
                                 String role, String decision) throws Exception {
        String body = """
                {"decisionNo": "%s", "role": "%s", "decision": "%s", "occurredAt": "2026-09-27T08:00:00Z"}
                """.formatted(decisionNo, role, decision);
        return mockMvc.perform(post("/api/manifests/{no}/corrections/{cno}/decisions", manifestNo, correctionNo)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String weightChange(int itemIndex, String newValue) {
        return """
                {"itemIndex": %d, "field": "WEIGHT", "newValue": "%s"}
                """.formatted(itemIndex, newValue);
    }

    private static String packageCountChange(int itemIndex, String newValue) {
        return """
                {"itemIndex": %d, "field": "PACKAGE_COUNT", "newValue": "%s"}
                """.formatted(itemIndex, newValue);
    }

    private static String categoryChange(int itemIndex, String newValue) {
        return """
                {"itemIndex": %d, "field": "WASTE_CATEGORY", "newValue": "%s"}
                """.formatted(itemIndex, newValue);
    }
}
