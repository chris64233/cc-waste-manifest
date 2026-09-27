package com.chris64233.cc.wastemanifest.correction;

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
    private CorrectionRepository corrections;

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

    // ---- 基本流程 ----

    @Test
    void weightCorrectionTakesEffectAfterAllRequiredApprovals() throws Exception {
        createCompletedManifest("R-001", 150);

        createCorrection("R-001", weightCorrectionBody("CR-1", "GENERATOR", "1", "95"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.baseVersionNo").value(1))
                .andExpect(jsonPath("$.changes[0].oldValue").value("100.000"))
                .andExpect(jsonPath("$.changes[0].newValue").value("95.000"))
                .andExpect(jsonPath("$.disputeImpact").value("NONE"));

        // 承运方与重量字段无关，无需确认
        decide("R-001", "CR-1", "D-1", "TRANSPORTER", "APPROVED", "2026-09-27T09:00:00Z")
                .andExpect(status().is(422));

        decide("R-001", "CR-1", "D-2", "GENERATOR", "APPROVED", "2026-09-27T09:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        decide("R-001", "CR-1", "D-3", "DISPOSER", "APPROVED", "2026-09-27T10:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EFFECTIVE"))
                .andExpect(jsonPath("$.resultVersionNo").value(2));

        // 当前有效版本为 v2，申报总重 145
        mockMvc.perform(get("/api/manifests/R-001/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("SUPERSEDED"))
                .andExpect(jsonPath("$[1].status").value("EFFECTIVE"))
                .andExpect(jsonPath("$[1].declaredTotalWeight").value(145.0))
                .andExpect(jsonPath("$[1].items[0].declaredWeight").value(95.0))
                .andExpect(jsonPath("$[1].items[1].declaredWeight").value(50.0));

        mockMvc.perform(get("/api/manifests/R-001/versions/diff?from=1&to=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes.length()").value(1))
                .andExpect(jsonPath("$.changes[0].field").value("WEIGHT"))
                .andExpect(jsonPath("$.changes[0].oldValue").value("100.000"))
                .andExpect(jsonPath("$.changes[0].newValue").value("95.000"));

        mockMvc.perform(get("/api/manifests/R-001"))
                .andExpect(jsonPath("$.currentVersionNo").value(2));
    }

    @Test
    void packageCountCorrectionRequiresAllThreeParties() throws Exception {
        createCompletedManifest("R-002", 150);
        createCorrection("R-002", """
                {"correctionNo": "CR-1", "applicantRole": "GENERATOR",
                 "reason": "包装清点错误", "evidenceRef": "photo-123",
                 "changes": [{"field": "PACKAGE_COUNT", "itemSeq": 1, "newValue": "12"}]}
                """).andExpect(status().isCreated());

        decide("R-002", "CR-1", "D-1", "GENERATOR", "APPROVED", "2026-09-27T09:00:00Z")
                .andExpect(jsonPath("$.status").value("PENDING"));
        decide("R-002", "CR-1", "D-2", "TRANSPORTER", "APPROVED", "2026-09-27T10:00:00Z")
                .andExpect(jsonPath("$.status").value("PENDING"));
        decide("R-002", "CR-1", "D-3", "DISPOSER", "APPROVED", "2026-09-27T11:00:00Z")
                .andExpect(jsonPath("$.status").value("EFFECTIVE"));

        mockMvc.perform(get("/api/manifests/R-002/versions"))
                .andExpect(jsonPath("$[1].items[0].packageCount").value(12));
    }

    @Test
    void wasteCategoryCorrectionRequiresRegulatorReview() throws Exception {
        createCompletedManifest("R-003", 150);
        createCorrection("R-003", """
                {"correctionNo": "CR-1", "applicantRole": "GENERATOR",
                 "reason": "类别申报错误", "evidenceRef": "lab-report-9",
                 "changes": [{"field": "WASTE_CATEGORY", "itemSeq": 2, "newValue": "HW49-REV"}]}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.disputeImpact").value("WASTE_CATEGORY_CHANGED"));

        decide("R-003", "CR-1", "D-1", "GENERATOR", "APPROVED", "2026-09-27T09:00:00Z")
                .andExpect(jsonPath("$.status").value("PENDING"));
        decide("R-003", "CR-1", "D-2", "DISPOSER", "APPROVED", "2026-09-27T10:00:00Z")
                .andExpect(jsonPath("$.status").value("PENDING"));
        // 监管复核前不得生效
        mockMvc.perform(get("/api/manifests/R-003/versions"))
                .andExpect(jsonPath("$.length()").value(1));
        decide("R-003", "CR-1", "D-3", "REGULATOR", "APPROVED", "2026-09-27T11:00:00Z")
                .andExpect(jsonPath("$.status").value("EFFECTIVE"));

        mockMvc.perform(get("/api/manifests/R-003/versions"))
                .andExpect(jsonPath("$[1].items[1].wasteCategory").value("HW49-REV"));
    }

    // ---- 创建约束 ----

    @Test
    void rejectCorrectionOnUncompletedManifest() throws Exception {
        createManifest("R-010");
        createCorrection("R-010", weightCorrectionBody("CR-1", "GENERATOR", "1", "95"))
                .andExpect(status().is(422));
    }

    @Test
    void rejectSecondActiveCorrectionOnSameManifest() throws Exception {
        createCompletedManifest("R-011", 150);
        createCorrection("R-011", weightCorrectionBody("CR-1", "GENERATOR", "1", "95"))
                .andExpect(status().isCreated());
        createCorrection("R-011", weightCorrectionBody("CR-2", "DISPOSER", "1", "90"))
                .andExpect(status().is(422));
    }

    @Test
    void rejectCorrectionWithSameOldAndNewValue() throws Exception {
        createCompletedManifest("R-012", 150);
        createCorrection("R-012", weightCorrectionBody("CR-1", "GENERATOR", "1", "100"))
                .andExpect(status().is(422));
    }

    @Test
    void rejectCorrectionWithUnknownItemSeq() throws Exception {
        createCompletedManifest("R-013", 150);
        createCorrection("R-013", weightCorrectionBody("CR-1", "GENERATOR", "9", "95"))
                .andExpect(status().is(422));
    }

    @Test
    void rejectRegulatorAsApplicant() throws Exception {
        createCompletedManifest("R-014", 150);
        createCorrection("R-014", weightCorrectionBody("CR-1", "REGULATOR", "1", "95"))
                .andExpect(status().is(422));
    }

    // ---- 幂等 ----

    @Test
    void correctionNoIdempotentReplayAndConflict() throws Exception {
        createCompletedManifest("R-020", 150);
        createCorrection("R-020", weightCorrectionBody("CR-1", "GENERATOR", "1", "95"))
                .andExpect(status().isCreated());

        // 同号同内容重放：返回当前结果，不新增
        mockMvc.perform(post("/api/manifests/R-020/corrections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weightCorrectionBody("CR-1", "GENERATOR", "1", "95")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correctionNo").value("CR-1"));
        mockMvc.perform(get("/api/manifests/R-020/record"))
                .andExpect(jsonPath("$.corrections.length()").value(1));

        // 同号异内容：409
        mockMvc.perform(post("/api/manifests/R-020/corrections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weightCorrectionBody("CR-1", "GENERATOR", "1", "90")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/manifests/R-020/corrections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weightCorrectionBody("CR-1", "DISPOSER", "1", "95")))
                .andExpect(status().isConflict());
    }

    @Test
    void decisionNoIdempotentReplayAndConflict() throws Exception {
        createCompletedManifest("R-021", 150);
        createCorrection("R-021", weightCorrectionBody("CR-1", "GENERATOR", "1", "95"))
                .andExpect(status().isCreated());

        decide("R-021", "CR-1", "D-1", "GENERATOR", "APPROVED", "2026-09-27T09:00:00Z")
                .andExpect(status().isOk());
        // 同号同内容重放
        decide("R-021", "CR-1", "D-1", "GENERATOR", "APPROVED", "2026-09-27T09:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisions.length()").value(1));
        // 同号异内容
        decide("R-021", "CR-1", "D-1", "GENERATOR", "REJECTED", "2026-09-27T09:00:00Z")
                .andExpect(status().isConflict());
        decide("R-021", "CR-1", "D-1", "DISPOSER", "APPROVED", "2026-09-27T09:00:00Z")
                .andExpect(status().isConflict());
    }

    // ---- 终止路径 ----

    @Test
    void rejectionTerminatesCorrection() throws Exception {
        createCompletedManifest("R-030", 150);
        createCorrection("R-030", weightCorrectionBody("CR-1", "GENERATOR", "1", "95"))
                .andExpect(status().isCreated());
        decide("R-030", "CR-1", "D-1", "DISPOSER", "REJECTED", "2026-09-27T09:00:00Z")
                .andExpect(jsonPath("$.status").value("REJECTED"));
        decide("R-030", "CR-1", "D-2", "GENERATOR", "APPROVED", "2026-09-27T10:00:00Z")
                .andExpect(status().is(422));
        mockMvc.perform(get("/api/manifests/R-030/versions"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void onlyApplicantCanWithdraw() throws Exception {
        createCompletedManifest("R-031", 150);
        createCorrection("R-031", weightCorrectionBody("CR-1", "GENERATOR", "1", "95"))
                .andExpect(status().isCreated());
        decide("R-031", "CR-1", "D-1", "DISPOSER", "WITHDRAWN", "2026-09-27T09:00:00Z")
                .andExpect(status().is(422));
        decide("R-031", "CR-1", "D-2", "GENERATOR", "WITHDRAWN", "2026-09-27T09:00:00Z")
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        // 撤回后可发起新一笔更正
        createCorrection("R-031", weightCorrectionBody("CR-2", "GENERATOR", "1", "96"))
                .andExpect(status().isCreated());
    }

    @Test
    void freezeTerminatesPendingCorrectionAndBlocksNewOnes() throws Exception {
        createCompletedManifest("R-032", 150);
        createCorrection("R-032", weightCorrectionBody("CR-1", "GENERATOR", "1", "95"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/manifests/R-032/freeze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regulatoryFrozen").value(true));

        mockMvc.perform(get("/api/manifests/R-032/corrections/CR-1"))
                .andExpect(jsonPath("$.status").value("FROZEN"));

        // 冻结后不得提交决定、不得发起新更正
        decide("R-032", "CR-1", "D-1", "GENERATOR", "APPROVED", "2026-09-27T09:00:00Z")
                .andExpect(status().is(422));
        createCorrection("R-032", weightCorrectionBody("CR-2", "GENERATOR", "1", "90"))
                .andExpect(status().is(422));
        mockMvc.perform(get("/api/manifests/R-032/versions"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void staleBaseVersionCorrectionCannotLand() throws Exception {
        createCompletedManifest("R-033", 150);
        // 第一笔更正生效，版本推进到 v2
        createCorrection("R-033", weightCorrectionBody("CR-1", "GENERATOR", "1", "95"))
                .andExpect(status().isCreated());
        decide("R-033", "CR-1", "D-1", "GENERATOR", "APPROVED", "2026-09-27T09:00:00Z");
        decide("R-033", "CR-1", "D-2", "DISPOSER", "APPROVED", "2026-09-27T10:00:00Z")
                .andExpect(jsonPath("$.status").value("EFFECTIVE"));

        // 第二笔由处置方发起、产生方已批准，但处置方（申请方）撤回，释放活动名额
        createCorrection("R-033", weightCorrectionBody("CR-2", "DISPOSER", "1", "93"))
                .andExpect(status().isCreated());
        decide("R-033", "CR-2", "D-3", "GENERATOR", "APPROVED", "2026-09-27T11:00:00Z");
        decide("R-033", "CR-2", "D-4", "DISPOSER", "WITHDRAWN", "2026-09-27T12:00:00Z")
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        // 第三笔在 v2 基础上生效到 v3
        createCorrection("R-033", weightCorrectionBody("CR-3", "GENERATOR", "1", "92"))
                .andExpect(status().isCreated());
        decide("R-033", "CR-3", "D-5", "GENERATOR", "APPROVED", "2026-09-27T13:00:00Z");
        decide("R-033", "CR-3", "D-6", "DISPOSER", "APPROVED", "2026-09-27T14:00:00Z")
                .andExpect(jsonPath("$.status").value("EFFECTIVE"));

        mockMvc.perform(get("/api/manifests/R-033"))
                .andExpect(jsonPath("$.currentVersionNo").value(3));
        mockMvc.perform(get("/api/manifests/R-033/versions/diff?from=2&to=3"))
                .andExpect(jsonPath("$.changes[0].oldValue").value("95.000"))
                .andExpect(jsonPath("$.changes[0].newValue").value("92.000"));
    }

    // ---- 争议影响评估 ----

    @Test
    void impactWouldHaveEnteredDispute() throws Exception {
        // 申报 150，实收 152，容差 7.5，原直接完成；申报改为 160 后偏差 8 > 8? 160*0.05=8，8<=8 仍容差内
        // 改为 165：|152-165|=13 > 8.25，本应进入争议
        createCompletedManifest("R-040", 152);
        createCorrection("R-040", weightCorrectionBody("CR-1", "GENERATOR", "1", "115"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disputeImpact").value("WOULD_HAVE_ENTERED_DISPUTE"));
    }

    @Test
    void impactDisputeWouldHaveBeenAvoided() throws Exception {
        // 申报 150，实收 140，偏差 10 > 7.5 进入争议，双方按 140 完成；
        // 申报改为 140：|140-140|=0，本不会争议
        createDisputedManifest("R-041");
        createCorrection("R-041", weightCorrectionBody("CR-1", "GENERATOR", "1", "90"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disputeImpact").value("DISPUTE_WOULD_HAVE_BEEN_AVOIDED"));
    }

    // ---- 完整档案查询 ----

    @Test
    void recordReturnsCurrentVersionDiffsAndTimeline() throws Exception {
        createCompletedManifest("R-050", 150);
        createCorrection("R-050", weightCorrectionBody("CR-1", "GENERATOR", "1", "95"))
                .andExpect(status().isCreated());
        decide("R-050", "CR-1", "D-1", "GENERATOR", "APPROVED", "2026-09-27T09:00:00Z");
        decide("R-050", "CR-1", "D-2", "DISPOSER", "APPROVED", "2026-09-27T10:00:00Z");

        mockMvc.perform(get("/api/manifests/R-050/record"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manifest.currentVersionNo").value(2))
                .andExpect(jsonPath("$.currentVersion.versionNo").value(2))
                .andExpect(jsonPath("$.currentVersion.status").value("EFFECTIVE"))
                .andExpect(jsonPath("$.versions.length()").value(2))
                .andExpect(jsonPath("$.versionDiffs.length()").value(1))
                .andExpect(jsonPath("$.versionDiffs[0].fromVersionNo").value(1))
                .andExpect(jsonPath("$.versionDiffs[0].toVersionNo").value(2))
                .andExpect(jsonPath("$.versionDiffs[0].changes[0].field").value("WEIGHT"))
                .andExpect(jsonPath("$.corrections.length()").value(1))
                .andExpect(jsonPath("$.corrections[0].decisions.length()").value(2))
                .andExpect(jsonPath("$.corrections[0].reason").value("重量申报错误"))
                .andExpect(jsonPath("$.corrections[0].evidenceRef").value("scale-ticket-7"))
                .andExpect(jsonPath("$.timeline.length()").value(3));
    }

    @Test
    void correctionNotFound() throws Exception {
        createCompletedManifest("R-060", 150);
        mockMvc.perform(get("/api/manifests/R-060/corrections/NOPE"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/manifests/NOPE/versions"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/manifests/NOPE/record"))
                .andExpect(status().isNotFound());
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

    /** 完成联单：处置方按 receivedWeight 收货（容差内直接完成）。 */
    private void createCompletedManifest(String manifestNo, double receivedWeight) throws Exception {
        createManifest(manifestNo);
        handover(manifestNo, "E-1", "GENERATOR", 150, "2026-09-26T08:00:00Z");
        handover(manifestNo, "E-2", "TRANSPORTER", 150, "2026-09-26T09:00:00Z");
        handover(manifestNo, "E-3", "DISPOSER", receivedWeight, "2026-09-26T10:00:00Z")
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    /** 完成一张经历过重量争议的联单：申报 150，实收 140，双方按 140 确认完成。 */
    private void createDisputedManifest(String manifestNo) throws Exception {
        createManifest(manifestNo);
        handover(manifestNo, "E-1", "GENERATOR", 150, "2026-09-26T08:00:00Z");
        handover(manifestNo, "E-2", "TRANSPORTER", 150, "2026-09-26T09:00:00Z");
        handover(manifestNo, "E-3", "DISPOSER", 140, "2026-09-26T10:00:00Z")
                .andExpect(jsonPath("$.status").value("WEIGHT_DISPUTE"));
        confirm(manifestNo, "E-4", "GENERATOR", 140, "2026-09-26T11:00:00Z");
        confirm(manifestNo, "E-5", "DISPOSER", 140, "2026-09-26T12:00:00Z")
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    private ResultActions handover(String manifestNo, String eventNo, String role,
                                   double weight, String occurredAt) throws Exception {
        return mockMvc.perform(post("/api/manifests/{no}/handover", manifestNo)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"eventNo": "%s", "role": "%s", "weight": %s, "occurredAt": "%s"}
                        """.formatted(eventNo, role, weight, occurredAt)));
    }

    private ResultActions confirm(String manifestNo, String eventNo, String role,
                                  double weight, String occurredAt) throws Exception {
        return mockMvc.perform(post("/api/manifests/{no}/dispute-confirmations", manifestNo)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"eventNo": "%s", "role": "%s", "confirmedWeight": %s, "occurredAt": "%s"}
                        """.formatted(eventNo, role, weight, occurredAt)));
    }

    private String weightCorrectionBody(String correctionNo, String applicantRole,
                                        String itemSeq, String newWeight) {
        return """
                {"correctionNo": "%s", "applicantRole": "%s",
                 "reason": "重量申报错误", "evidenceRef": "scale-ticket-7",
                 "changes": [{"field": "WEIGHT", "itemSeq": %s, "newValue": "%s"}]}
                """.formatted(correctionNo, applicantRole, itemSeq, newWeight);
    }

    private ResultActions createCorrection(String manifestNo, String body) throws Exception {
        return mockMvc.perform(post("/api/manifests/{no}/corrections", manifestNo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions decide(String manifestNo, String correctionNo, String decisionNo,
                                 String role, String value, String occurredAt) throws Exception {
        return mockMvc.perform(post("/api/manifests/{no}/corrections/{cno}/decisions",
                        manifestNo, correctionNo)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"decisionNo": "%s", "role": "%s", "value": "%s", "occurredAt": "%s"}
                        """.formatted(decisionNo, role, value, occurredAt)));
    }
}
