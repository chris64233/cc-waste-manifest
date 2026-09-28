package com.chris64233.cc.wastemanifest.incident;

import com.chris64233.cc.wastemanifest.correction.CorrectionDecisionRepository;
import com.chris64233.cc.wastemanifest.correction.CorrectionRepository;
import com.chris64233.cc.wastemanifest.correction.ManifestVersionRepository;
import com.chris64233.cc.wastemanifest.manifest.ManifestEventRepository;
import com.chris64233.cc.wastemanifest.manifest.ManifestRepository;
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
class IncidentApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ManifestRepository manifests;

    @Autowired
    private ManifestEventRepository events;

    @Autowired
    private ManifestVersionRepository versions;

    @Autowired
    private CorrectionRepository corrections;

    @Autowired
    private CorrectionDecisionRepository correctionDecisions;

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private IncidentPlanRepository plans;

    @Autowired
    private IncidentPlanDecisionRepository decisions;

    @Autowired
    private IncidentPackageSplitRepository splits;

    @Autowired
    private TransportSegmentRepository segments;

    @Autowired
    private SegmentEventRepository segmentEvents;

    @Autowired
    private LossRecordRepository losses;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        segmentEvents.deleteAll();
        splits.deleteAll();
        segments.deleteAll();
        losses.deleteAll();
        decisions.deleteAll();
        plans.deleteAll();
        incidents.deleteAll();
        correctionDecisions.deleteAll();
        corrections.deleteAll();
        versions.deleteAll();
        events.deleteAll();
        manifests.deleteAll();
    }

    @Test
    void leakFreezesPackagesAndLossRecordAfterAllConfirmations() throws Exception {
        moveToTransporterCustody("I-001");

        register("I-001", """
                {"eventNo": "IE-1", "type": "LEAK",
                 "occurredAt": "2026-09-26T09:30:00Z", "location": "G42 K120",
                 "evidenceRef": "photo-leak-1",
                 "affectedPackages": [{"itemSeq": 1, "quantity": 3}]}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.baseVersionNo").value(1))
                .andExpect(jsonPath("$.type").value("LEAK"))
                .andExpect(jsonPath("$.affectedPackages[0].wasteCategory").value("HW08"))
                .andExpect(jsonPath("$.affectedPackages[0].quantity").value(3));

        // 冻结期间不能继续正常交接
        handover("I-001", "E-3", "DISPOSER", 150, "2026-09-26T10:00:00Z")
                .andExpect(status().is(422));

        // 涉及数量变化，需监管决定；未齐确认前只是 PENDING
        createPlan("I-001", "IE-1", """
                {"planNo": "PL-1", "quantityChanged": true}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.regulatorRequired").value(true));

        decide("I-001", "IE-1", "PL-1", "PD-1", "GENERATOR", "APPROVED", "2026-09-26T11:00:00Z")
                .andExpect(jsonPath("$.status").value("PENDING"));
        decide("I-001", "IE-1", "PL-1", "PD-2", "TRANSPORTER", "APPROVED", "2026-09-26T12:00:00Z")
                .andExpect(jsonPath("$.status").value("PENDING"));
        decide("I-001", "IE-1", "PL-1", "PD-3", "OLD_DISPOSER", "APPROVED", "2026-09-26T13:00:00Z")
                .andExpect(jsonPath("$.status").value("PENDING"));
        // 泄漏无新处置方，新处置方确认与本方案无关
        decide("I-001", "IE-1", "PL-1", "PD-X", "NEW_DISPOSER", "APPROVED", "2026-09-26T13:30:00Z")
                .andExpect(status().is(422));
        decide("I-001", "IE-1", "PL-1", "PD-4", "REGULATOR", "APPROVED", "2026-09-26T14:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EFFECTIVE"))
                .andExpect(jsonPath("$.lossNo").value("PL-1-LOSS"))
                .andExpect(jsonPath("$.splits[0].originalQuantity").value(10))
                .andExpect(jsonPath("$.splits[0].affectedQuantity").value(3))
                .andExpect(jsonPath("$.splits[0].unaffectedQuantity").value(7))
                .andExpect(jsonPath("$.splits[0].targetType").value("LOSS"));

        mockMvc.perform(get("/api/manifests/I-001/incidents/IE-1"))
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        mockMvc.perform(get("/api/manifests/I-001/losses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lossNo").value("PL-1-LOSS"))
                .andExpect(jsonPath("$[0].lossType").value("LEAK"))
                .andExpect(jsonPath("$[0].packages[0].unaffectedQuantity").value(7));

        // 方案一次性生效后，未受影响部分继续沿原联单流转至完成
        handover("I-001", "E-3", "DISPOSER", 150, "2026-09-26T15:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void diversionCreatesConnectedSegmentDeliveredToNewDisposer() throws Exception {
        moveToTransporterCustody("I-002");

        register("I-002", """
                {"eventNo": "IE-2", "type": "DIVERSION",
                 "occurredAt": "2026-09-26T09:30:00Z", "location": "G4 K300",
                 "evidenceRef": "route-order-7",
                 "affectedPackages": [{"itemSeq": 1, "quantity": 4}]}
                """).andExpect(status().isCreated());

        // 改道必须指定新处置方与预计到达时间
        createPlan("I-002", "IE-2", """
                {"planNo": "PL-BAD", "quantityChanged": false}
                """).andExpect(status().is(422));
        // 新处置方不能与原处置方相同
        createPlan("I-002", "IE-2", """
                {"planNo": "PL-BAD2", "newDisposerId": "disp-1",
                 "estimatedArrivalAt": "2026-09-27T08:00:00Z"}
                """).andExpect(status().is(422));
        createPlan("I-002", "IE-2", """
                {"planNo": "PL-2", "newDisposerId": "disp-2",
                 "estimatedArrivalAt": "2026-09-27T08:00:00Z"}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.regulatorRequired").value(false));

        decide("I-002", "IE-2", "PL-2", "PD-1", "GENERATOR", "APPROVED", "2026-09-26T11:00:00Z");
        decide("I-002", "IE-2", "PL-2", "PD-2", "TRANSPORTER", "APPROVED", "2026-09-26T12:00:00Z");
        decide("I-002", "IE-2", "PL-2", "PD-3", "OLD_DISPOSER", "APPROVED", "2026-09-26T13:00:00Z")
                .andExpect(jsonPath("$.status").value("PENDING"));
        // 无类别/数量变化，监管无需决定
        decide("I-002", "IE-2", "PL-2", "PD-X", "REGULATOR", "APPROVED", "2026-09-26T13:30:00Z")
                .andExpect(status().is(422));
        decide("I-002", "IE-2", "PL-2", "PD-4", "NEW_DISPOSER", "APPROVED", "2026-09-26T14:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EFFECTIVE"))
                .andExpect(jsonPath("$.segmentNo").value("PL-2-SEG"))
                .andExpect(jsonPath("$.splits[0].targetType").value("SEGMENT"))
                .andExpect(jsonPath("$.splits[0].unaffectedQuantity").value(6));

        mockMvc.perform(get("/api/manifests/I-002/segments"))
                .andExpect(jsonPath("$[0].segmentNo").value("PL-2-SEG"))
                .andExpect(jsonPath("$[0].newDisposerId").value("disp-2"))
                .andExpect(jsonPath("$[0].status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$[0].packages[0].affectedQuantity").value(4));

        // 新运输段独立交接链：承运方发运 → 新处置方收货
        segmentHandover("I-002", "PL-2-SEG", "SE-1", "DISPOSER", 40, "2026-09-26T18:00:00Z")
                .andExpect(status().is(422));
        segmentHandover("I-002", "PL-2-SEG", "SE-1", "TRANSPORTER", 40, "2026-09-26T16:00:00Z")
                .andExpect(status().isOk());
        segmentHandover("I-002", "PL-2-SEG", "SE-2", "DISPOSER", 40, "2026-09-27T07:30:00Z")
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/manifests/I-002/segments/PL-2-SEG/timeline"))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/manifests/I-002/segments"))
                .andExpect(jsonPath("$[0].status").value("DELIVERED"));

        // 未受影响部分继续沿原联单流转
        handover("I-002", "E-3", "DISPOSER", 150, "2026-09-26T17:00:00Z")
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void incidentAndPlanAndDecisionAreIdempotentWithConflict() throws Exception {
        moveToTransporterCustody("I-010");
        String body = """
                {"eventNo": "IE-3", "type": "LOSS",
                 "occurredAt": "2026-09-26T09:30:00Z", "location": "S20",
                 "evidenceRef": "police-report-1",
                 "affectedPackages": [{"itemSeq": 2, "quantity": 2}]}
                """;
        register("I-010", body).andExpect(status().isCreated());
        register("I-010", body).andExpect(status().isCreated());
        mockMvc.perform(get("/api/manifests/I-010/incidents")).andExpect(jsonPath("$.length()").value(1));
        // 同号异内容
        register("I-010", body.replace("\"quantity\": 2", "\"quantity\": 3"))
                .andExpect(status().isConflict());
        register("I-010", body.replace("LOSS", "LEAK"))
                .andExpect(status().isConflict());

        String planBody = """
                {"planNo": "PL-3", "quantityChanged": true, "categoryChanged": false}
                """;
        createPlan("I-010", "IE-3", planBody).andExpect(status().isCreated());
        createPlan("I-010", "IE-3", planBody).andExpect(status().isCreated());
        mockMvc.perform(get("/api/manifests/I-010/incidents/IE-3/plans/PL-3"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        createPlan("I-010", "IE-3", planBody.replace("\"quantityChanged\": true", "\"quantityChanged\": false"))
                .andExpect(status().isConflict());

        decide("I-010", "IE-3", "PL-3", "PD-1", "GENERATOR", "APPROVED", "2026-09-26T11:00:00Z")
                .andExpect(status().isOk());
        decide("I-010", "IE-3", "PL-3", "PD-1", "GENERATOR", "APPROVED", "2026-09-26T11:00:00Z")
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/manifests/I-010/incidents/IE-3/plans/PL-3"))
                .andExpect(jsonPath("$.decisions.length()").value(1));
        decide("I-010", "IE-3", "PL-3", "PD-1", "GENERATOR", "REJECTED", "2026-09-26T11:00:00Z")
                .andExpect(status().isConflict());
    }

    @Test
    void registrationGuardsAndRejectionAndFreeze() throws Exception {
        // 未进入运输环节不能登记
        createManifest("I-020");
        register("I-020", lossBody("IE-4", 1)).andExpect(status().is(422));

        moveToTransporterCustody("I-021");
        register("I-021", lossBody("IE-5", 1)).andExpect(status().isCreated());
        // 同一联单只能有一笔未处置完成异常
        register("I-021", lossBody("IE-6", 1)).andExpect(status().is(422));
        // 受影响数量不能超过包装总数
        register("I-022-X", lossBody("IE-7", 1)).andExpect(status().isNotFound());
        createManifest("I-022");
        moveOnlyToTransit("I-022");
        register("I-022", """
                {"eventNo": "IE-7", "type": "LEAK",
                 "occurredAt": "2026-09-26T08:30:00Z", "location": "road",
                 "evidenceRef": "ev",
                 "affectedPackages": [{"itemSeq": 1, "quantity": 999}]}
                """).andExpect(status().is(422));

        // 责任方拒绝终止方案，之后可重新提方案并处置完成
        createPlan("I-021", "IE-5", """
                {"planNo": "PL-4", "quantityChanged": true}
                """).andExpect(status().isCreated());
        decide("I-021", "IE-5", "PL-4", "PD-1", "OLD_DISPOSER", "REJECTED", "2026-09-26T11:00:00Z")
                .andExpect(jsonPath("$.status").value("REJECTED"));
        decide("I-021", "IE-5", "PL-4", "PD-2", "GENERATOR", "APPROVED", "2026-09-26T12:00:00Z")
                .andExpect(status().is(422));
        // 仅承运方可撤回
        createPlan("I-021", "IE-5", """
                {"planNo": "PL-5", "quantityChanged": true}
                """).andExpect(status().isCreated());
        decide("I-021", "IE-5", "PL-5", "PD-3", "GENERATOR", "WITHDRAWN", "2026-09-26T13:00:00Z")
                .andExpect(status().is(422));
        decide("I-021", "IE-5", "PL-5", "PD-4", "TRANSPORTER", "WITHDRAWN", "2026-09-26T14:00:00Z")
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        // 撤回后可提出新方案
        createPlan("I-021", "IE-5", """
                {"planNo": "PL-6", "quantityChanged": true}
                """).andExpect(status().isCreated());
        approveRequiredLoss("I-021", "IE-5", "PL-6", "2026-09-26T15:00:00Z");
        mockMvc.perform(get("/api/manifests/I-021/incidents/IE-5"))
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // 冻结终止确认中的方案，且不得再登记异常
        moveToTransporterCustody("I-023");
        register("I-023", lossBody("IE-8", 1)).andExpect(status().isCreated());
        createPlan("I-023", "IE-8", """
                {"planNo": "PL-7", "quantityChanged": true}
                """).andExpect(status().isCreated());
        mockMvc.perform(post("/api/manifests/I-023/freeze"))
                .andExpect(jsonPath("$.regulatoryFrozen").value(true));
        mockMvc.perform(get("/api/manifests/I-023/incidents/IE-8/plans/PL-7"))
                .andExpect(jsonPath("$.status").value("FROZEN"));
        decide("I-023", "IE-8", "PL-7", "PD-9", "GENERATOR", "APPROVED", "2026-09-26T15:00:00Z")
                .andExpect(status().is(422));
        register("I-023", lossBody("IE-9", 1)).andExpect(status().is(422));
        // 终止不留半条运输链：没有任何拆分、损失、运输段
        mockMvc.perform(get("/api/manifests/I-023/losses")).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/manifests/I-023/segments")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void incidentRecordShowsEvidenceDecisionsSplitsAndChain() throws Exception {
        moveToTransporterCustody("I-030");
        register("I-030", lossBody("IE-10", 2)).andExpect(status().isCreated());
        createPlan("I-030", "IE-10", """
                {"planNo": "PL-8", "quantityChanged": true}
                """).andExpect(status().isCreated());
        approveRequiredLoss("I-030", "IE-10", "PL-8", "2026-09-26T10:00:00Z");
        handover("I-030", "E-3", "DISPOSER", 150, "2026-09-26T16:00:00Z");

        mockMvc.perform(get("/api/manifests/I-030/incident-record"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.record.manifest.status").value("COMPLETED"))
                .andExpect(jsonPath("$.incidents.length()").value(1))
                .andExpect(jsonPath("$.incidents[0].evidenceRef").value("police-x"))
                .andExpect(jsonPath("$.incidents[0].plans[0].decisions.length()").value(4))
                .andExpect(jsonPath("$.incidents[0].plans[0].splits[0].affectedQuantity").value(2))
                .andExpect(jsonPath("$.incidents[0].plans[0].splits[0].unaffectedQuantity").value(8))
                .andExpect(jsonPath("$.losses[0].lossNo").value("PL-8-LOSS"))
                .andExpect(jsonPath("$.record.timeline.length()").value(3))
                .andExpect(jsonPath("$.segments.length()").value(0));
    }

    // ---- 辅助 ----

    private void createManifest(String manifestNo) throws Exception {
        mockMvc.perform(post("/api/manifests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
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
                                """.formatted(manifestNo)))
                .andExpect(status().isCreated());
    }

    private void moveOnlyToTransit(String manifestNo) throws Exception {
        handover(manifestNo, "E-" + manifestNo + "-1", "GENERATOR", 150, "2026-09-26T08:00:00Z");
    }

    private void moveToTransporterCustody(String manifestNo) throws Exception {
        createManifest(manifestNo);
        handover(manifestNo, "E-" + manifestNo + "-1", "GENERATOR", 150, "2026-09-26T08:00:00Z");
        handover(manifestNo, "E-" + manifestNo + "-2", "TRANSPORTER", 150, "2026-09-26T09:00:00Z");
    }

    private String lossBody(String eventNo, int quantity) {
        return """
                {"eventNo": "%s", "type": "LOSS",
                 "occurredAt": "2026-09-26T09:30:00Z", "location": "S20",
                 "evidenceRef": "police-x",
                 "affectedPackages": [{"itemSeq": 1, "quantity": %d}]}
                """.formatted(eventNo, quantity);
    }

    private void approveRequiredLoss(String manifestNo, String eventNo, String planNo, String firstTime)
            throws Exception {
        java.time.Instant t = java.time.Instant.parse(firstTime);
        String[] labels = {"-G", "-T", "-O", "-R"};
        String[] roles = {"GENERATOR", "TRANSPORTER", "OLD_DISPOSER", "REGULATOR"};
        for (int i = 0; i < 4; i++) {
            decide(manifestNo, eventNo, planNo, planNo + labels[i], roles[i], "APPROVED",
                    t.plusSeconds(3600L * i).toString())
                    .andExpect(i < 3 ? jsonPath("$.status").value("PENDING")
                            : jsonPath("$.status").value("EFFECTIVE"));
        }
    }

    private ResultActions register(String manifestNo, String body) throws Exception {
        return mockMvc.perform(post("/api/manifests/{no}/incidents", manifestNo)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions createPlan(String manifestNo, String eventNo, String body) throws Exception {
        return mockMvc.perform(post("/api/manifests/{no}/incidents/{e}/plans", manifestNo, eventNo)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions decide(String manifestNo, String eventNo, String planNo, String decisionNo,
                                 String role, String value, String occurredAt) throws Exception {
        return mockMvc.perform(post(
                        "/api/manifests/{no}/incidents/{e}/plans/{p}/decisions",
                        manifestNo, eventNo, planNo)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"decisionNo": "%s", "role": "%s", "value": "%s", "occurredAt": "%s"}
                        """.formatted(decisionNo, role, value, occurredAt)));
    }

    private ResultActions handover(String manifestNo, String eventNo, String role,
                                   double weight, String occurredAt) throws Exception {
        return mockMvc.perform(post("/api/manifests/{no}/handover", manifestNo)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"eventNo": "%s", "role": "%s", "weight": %s, "occurredAt": "%s"}
                        """.formatted(eventNo, role, weight, occurredAt)));
    }

    private ResultActions segmentHandover(String manifestNo, String segmentNo, String eventNo,
                                          String role, double weight, String occurredAt) throws Exception {
        return mockMvc.perform(post("/api/manifests/{no}/segments/{s}/handover",
                        manifestNo, segmentNo)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"eventNo": "%s", "role": "%s", "weight": %s, "occurredAt": "%s"}
                        """.formatted(eventNo, role, weight, occurredAt)));
    }
}
