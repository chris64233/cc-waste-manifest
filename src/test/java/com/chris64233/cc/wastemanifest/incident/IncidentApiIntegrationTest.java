package com.chris64233.cc.wastemanifest.incident;

import com.chris64233.cc.wastemanifest.correction.CorrectionDecisionRepository;
import com.chris64233.cc.wastemanifest.correction.CorrectionRepository;
import com.chris64233.cc.wastemanifest.correction.ManifestVersionRepository;
import com.chris64233.cc.wastemanifest.manifest.ActiveManifestOpRepository;
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
    private TransportIncidentRepository incidents;

    @Autowired
    private IncidentDecisionRepository decisions;

    @Autowired
    private TransportSegmentRepository segments;

    @Autowired
    private SegmentHandoverEventRepository segmentEvents;

    @Autowired
    private PackageFreezeRepository freezes;

    @Autowired
    private ActiveManifestOpRepository activeOps;

    @Autowired
    private com.chris64233.cc.wastemanifest.manifest.ManifestFlowAdjustmentRepository flowAdjustments;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        decisions.deleteAll();
        segmentEvents.deleteAll();
        segments.deleteAll();
        incidents.deleteAll();
        freezes.deleteAll();
        activeOps.deleteAll();
        correctionDecisions.deleteAll();
        corrections.deleteAll();
        versions.deleteAll();
        events.deleteAll();
        flowAdjustments.deleteAll();
        manifests.deleteAll();
    }

    // ---- 登记约束 ----

    @Test
    void registerLeakFreezesAffectedPackagesAndBlocksHandover() throws Exception {
        inTransit("I-001");

        register("I-001", leakBody("INC-1", 4, 40))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.type").value("LEAK"))
                .andExpect(jsonPath("$.baseVersionNo").value(1))
                .andExpect(jsonPath("$.regulatorRequired").value(false))
                .andExpect(jsonPath("$.affectedLines[0].affectedPackageCount").value(4));

        // 冻结期间不能继续正常交接
        handover("I-001", "E-3", "TRANSPORTER", 100, "2026-09-26T12:00:00Z")
                .andExpect(status().is(422));

        mockMvc.perform(get("/api/manifests/I-001"))
                .andExpect(jsonPath("$.packageFreezeActive").value(true));
    }

    @Test
    void rejectIncidentWhenNotInTransit() throws Exception {
        createManifest("I-002");
        register("I-002", leakBody("INC-1", 1, 10)).andExpect(status().is(422));
    }

    @Test
    void rejectAffectedPackagesExceedingItem() throws Exception {
        inTransit("I-003");
        register("I-003", leakBody("INC-1", 11, 100)).andExpect(status().is(422));
        register("I-003", """
                {"incidentNo": "INC-2", "type": "LEAK",
                 "occurredAt": "2026-09-26T11:00:00Z", "location": "G42 K120",
                 "evidenceRef": "photo-1",
                 "affectedLines": [{"itemSeq": 9, "packageCount": 1, "weight": 10}]}
                """).andExpect(status().is(422));
    }

    @Test
    void rejectDuplicateItemSeqInOneIncident() throws Exception {
        inTransit("I-004");
        register("I-004", """
                {"incidentNo": "INC-1", "type": "LEAK",
                 "occurredAt": "2026-09-26T11:00:00Z", "location": "G42 K120",
                 "evidenceRef": "photo-1",
                 "affectedLines": [
                   {"itemSeq": 1, "packageCount": 1, "weight": 10},
                   {"itemSeq": 1, "packageCount": 1, "weight": 10}
                 ]}
                """).andExpect(status().is(422));
    }

    @Test
    void diversionRequiresNewDisposerAndEta() throws Exception {
        inTransit("I-005");
        register("I-005", """
                {"incidentNo": "INC-1", "type": "DIVERSION",
                 "occurredAt": "2026-09-26T11:00:00Z", "location": "G42 K120",
                 "evidenceRef": "notice-1",
                 "affectedLines": [{"itemSeq": 1, "packageCount": 10, "weight": 100}]}
                """).andExpect(status().is(422));

        register("I-005", """
                {"incidentNo": "INC-2", "type": "DIVERSION",
                 "occurredAt": "2026-09-26T11:00:00Z", "location": "G42 K120",
                 "evidenceRef": "notice-1",
                 "affectedLines": [{"itemSeq": 1, "packageCount": 10, "weight": 100}],
                 "newDisposerId": "disp-2",
                 "estimatedArrivalAt": "2026-09-26T18:00:00Z"}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.newDisposerId").value("disp-2"));
    }

    @Test
    void rejectNewDisposerOnNonDiversion() throws Exception {
        inTransit("I-006");
        register("I-006", """
                {"incidentNo": "INC-1", "type": "LEAK",
                 "occurredAt": "2026-09-26T11:00:00Z", "location": "G42 K120",
                 "evidenceRef": "photo-1",
                 "affectedLines": [{"itemSeq": 1, "packageCount": 2, "weight": 20}],
                 "newDisposerId": "disp-2"}
                """).andExpect(status().is(422));
    }

    @Test
    void rejectTimeReversal() throws Exception {
        inTransit("I-007");
        register("I-007", leakBodyAt("INC-1", 2, 20, "2026-09-26T07:30:00Z"))
                .andExpect(status().is(422));
    }

    // ---- 幂等与冲突 ----

    @Test
    void incidentNoIdempotentReplayAndConflict() throws Exception {
        inTransit("I-010");
        register("I-010", leakBody("INC-1", 4, 40)).andExpect(status().isCreated());
        register("I-010", leakBody("INC-1", 4, 40)).andExpect(status().isCreated());
        mockMvc.perform(get("/api/manifests/I-010/incidents"))
                .andExpect(jsonPath("$.length()").value(1));

        // 同号异内容：409
        register("I-010", leakBody("INC-1", 5, 50)).andExpect(status().isConflict());
    }

    @Test
    void decisionNoIdempotentReplayAndConflict() throws Exception {
        inTransit("I-011");
        register("I-011", leakBody("INC-1", 4, 40)).andExpect(status().isCreated());

        decide("I-011", "INC-1", "ID-1", "GENERATOR", "APPROVED", "2026-09-26T12:00:00Z")
                .andExpect(status().isOk());
        decide("I-011", "INC-1", "ID-1", "GENERATOR", "APPROVED", "2026-09-26T12:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisions.length()").value(1));
        decide("I-011", "INC-1", "ID-1", "GENERATOR", "REJECTED", "2026-09-26T12:00:00Z")
                .andExpect(status().isConflict());
    }

    // ---- 生效与拆分 ----

    @Test
    void leakPlanSplitsPackagesOnceAndBothChainsFlow() throws Exception {
        // 明细 10 桶 / 100 kg；泄漏 4 桶 / 40 kg；未受影响 6 桶继续原联单，受影响 4 桶进新运输段
        inTransit("I-020");
        register("I-020", leakBody("INC-1", 4, 40)).andExpect(status().isCreated());

        decide("I-020", "INC-1", "ID-1", "GENERATOR", "APPROVED", "2026-09-26T12:00:00Z")
                .andExpect(jsonPath("$.status").value("PENDING"));
        decide("I-020", "INC-1", "ID-2", "TRANSPORTER", "APPROVED", "2026-09-26T12:05:00Z")
                .andExpect(jsonPath("$.status").value("PENDING"));
        decide("I-020", "INC-1", "ID-3", "OLD_DISPOSER", "APPROVED", "2026-09-26T12:10:00Z")
                .andExpect(jsonPath("$.status").value("EFFECTIVE"))
                .andExpect(jsonPath("$.segments.length()").value(1));

        // 新运输段：与原联单相连、4 桶 40kg、前往原处置方
        mockMvc.perform(get("/api/manifests/I-020/segments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].segmentNo").value("INC-1-S1"))
                .andExpect(jsonPath("$[0].type").value("NEW_TRANSPORT"))
                .andExpect(jsonPath("$[0].parentManifestNo").value("I-020"))
                .andExpect(jsonPath("$[0].destinationDisposerId").value("disp-1"))
                .andExpect(jsonPath("$[0].status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$[0].declaredWeight").value(40.0))
                .andExpect(jsonPath("$[0].items[0].sourceItemSeq").value(1))
                .andExpect(jsonPath("$[0].items[0].packageCount").value(4));

        // 冻结已释放，未受影响 6 桶继续沿原联单流转；处置方按剩余 60 收货，容差内完成
        handover("I-020", "E-3", "TRANSPORTER", 60, "2026-09-26T13:00:00Z")
                .andExpect(jsonPath("$.status").value("RECEIVED_BY_TRANSPORTER"))
                .andExpect(jsonPath("$.remainingDeclaredWeight").value(60.0));
        handover("I-020", "E-4", "DISPOSER", 60, "2026-09-26T14:00:00Z")
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        // v1 快照只固化继续流转部分
        mockMvc.perform(get("/api/manifests/I-020/versions"))
                .andExpect(jsonPath("$[0].status").value("EFFECTIVE"))
                .andExpect(jsonPath("$[0].declaredTotalWeight").value(60.0))
                .andExpect(jsonPath("$[0].items[0].packageCount").value(6));

        // 受影响 4 桶沿新运输段交付、接收
        segmentHandover("I-020", "INC-1-S1", "SE-1", "TRANSPORTER", 40, "2026-09-26T15:00:00Z")
                .andExpect(jsonPath("$.status").value("DELIVERED"));
        segmentHandover("I-020", "INC-1-S1", "SE-2", "DISPOSER", 40, "2026-09-26T16:00:00Z")
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.receivedWeight").value(40.0));
    }

    @Test
    void lossPlanRequiresRegulatorAndCreatesLossRecord() throws Exception {
        inTransit("I-021");
        register("I-021", lossBody("INC-1", 3, 30)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.regulatorRequired").value(true));

        // 三方齐备但缺监管决定，不生效
        decide("I-021", "INC-1", "ID-1", "GENERATOR", "APPROVED", "2026-09-26T12:00:00Z");
        decide("I-021", "INC-1", "ID-2", "TRANSPORTER", "APPROVED", "2026-09-26T12:05:00Z");
        decide("I-021", "INC-1", "ID-3", "OLD_DISPOSER", "APPROVED", "2026-09-26T12:10:00Z")
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(get("/api/manifests/I-021/segments")).andExpect(jsonPath("$.length()").value(0));

        // 无关角色不能决定
        decide("I-021", "INC-1", "ID-X", "NEW_DISPOSER", "APPROVED", "2026-09-26T12:15:00Z")
                .andExpect(status().is(422));

        decide("I-021", "INC-1", "ID-4", "REGULATOR", "APPROVED", "2026-09-26T12:20:00Z")
                .andExpect(jsonPath("$.status").value("EFFECTIVE"));

        mockMvc.perform(get("/api/manifests/I-021/segments"))
                .andExpect(jsonPath("$[0].type").value("LOSS"))
                .andExpect(jsonPath("$[0].status").value("LOST"))
                .andExpect(jsonPath("$[0].items[0].packageCount").value(3));

        // 损失记录不能交接
        segmentHandover("I-021", "INC-1-S1", "SE-1", "TRANSPORTER", 30, "2026-09-26T13:00:00Z")
                .andExpect(status().is(422));
    }

    @Test
    void diversionRequiresBothDisposersApprovals() throws Exception {
        inTransit("I-022");
        register("I-022", diversionBody("INC-1", 10, 100)).andExpect(status().isCreated());

        decide("I-022", "INC-1", "ID-1", "GENERATOR", "APPROVED", "2026-09-26T12:00:00Z");
        decide("I-022", "INC-1", "ID-2", "TRANSPORTER", "APPROVED", "2026-09-26T12:05:00Z");
        decide("I-022", "INC-1", "ID-3", "OLD_DISPOSER", "APPROVED", "2026-09-26T12:10:00Z")
                .andExpect(jsonPath("$.status").value("PENDING"));
        // 改道不需要监管（无类别/数量变化），但新处置方必须确认
        decide("I-022", "INC-1", "ID-4", "REGULATOR", "APPROVED", "2026-09-26T12:15:00Z")
                .andExpect(status().is(422));
        decide("I-022", "INC-1", "ID-5", "NEW_DISPOSER", "APPROVED", "2026-09-26T12:20:00Z")
                .andExpect(jsonPath("$.status").value("EFFECTIVE"));

        mockMvc.perform(get("/api/manifests/I-022/segments"))
                .andExpect(jsonPath("$[0].type").value("NEW_TRANSPORT"))
                .andExpect(jsonPath("$[0].destinationDisposerId").value("disp-2"))
                .andExpect(jsonPath("$[0].estimatedArrivalAt").value("2026-09-26T18:00:00Z"));
    }

    @Test
    void leakWithQuantityChangeRequiresRegulator() throws Exception {
        inTransit("I-023");
        register("I-023", """
                {"incidentNo": "INC-1", "type": "LEAK",
                 "occurredAt": "2026-09-26T11:00:00Z", "location": "G42 K120",
                 "evidenceRef": "photo-1",
                 "affectedLines": [{"itemSeq": 1, "packageCount": 4, "weight": 40}],
                 "resultLines": [{"packageCount": 3, "weight": 28}]}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.regulatorRequired").value(true))
                .andExpect(jsonPath("$.affectedLines[0].resultPackageCount").value(3))
                .andExpect(jsonPath("$.affectedLines[0].resultWeight").value(28.0));

        for (String[] d : new String[][]{
                {"ID-1", "GENERATOR", "2026-09-26T12:00:00Z"},
                {"ID-2", "TRANSPORTER", "2026-09-26T12:05:00Z"},
                {"ID-3", "OLD_DISPOSER", "2026-09-26T12:10:00Z"}}) {
            decide("I-023", "INC-1", d[0], d[1], "APPROVED", d[2])
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }
        decide("I-023", "INC-1", "ID-4", "REGULATOR", "APPROVED", "2026-09-26T12:20:00Z")
                .andExpect(jsonPath("$.status").value("EFFECTIVE"));

        // 原联单按受影响原始量 40 拆出（剩 60）；新运输段按处置口径 3 桶 28kg
        mockMvc.perform(get("/api/manifests/I-023"))
                .andExpect(jsonPath("$.remainingDeclaredWeight").value(60.0));
        mockMvc.perform(get("/api/manifests/I-023/segments"))
                .andExpect(jsonPath("$[0].declaredWeight").value(28.0))
                .andExpect(jsonPath("$[0].items[0].packageCount").value(3));
    }

    @Test
    void fullDiversionClosesOriginalManifestWithZeroRemaining() throws Exception {
        // 全部 10 桶改道：原联单剩余 0，结案 CLOSED；全部包装沿新运输段前往新处置方
        inTransit("I-024");
        register("I-024", diversionBody("INC-1", 10, 100)).andExpect(status().isCreated());
        decide("I-024", "INC-1", "ID-1", "GENERATOR", "APPROVED", "2026-09-26T12:00:00Z");
        decide("I-024", "INC-1", "ID-2", "TRANSPORTER", "APPROVED", "2026-09-26T12:05:00Z");
        decide("I-024", "INC-1", "ID-3", "OLD_DISPOSER", "APPROVED", "2026-09-26T12:10:00Z");
        decide("I-024", "INC-1", "ID-5", "NEW_DISPOSER", "APPROVED", "2026-09-26T12:20:00Z")
                .andExpect(jsonPath("$.status").value("EFFECTIVE"));

        mockMvc.perform(get("/api/manifests/I-024"))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.remainingDeclaredWeight").value(0.0));
        // 结案后原联单不能再交接
        handover("I-024", "E-3", "TRANSPORTER", 0, "2026-09-26T13:00:00Z")
                .andExpect(status().is(400));
        handover("I-024", "E-3", "TRANSPORTER", 100, "2026-09-26T13:00:00Z")
                .andExpect(status().is(422));
    }

    // ---- 连续异常：在剩余包装上二次拆分 ----

    @Test
    void secondIncidentOperatesOnRemainingPackages() throws Exception {
        inTransit("I-070");
        // 第一笔泄漏 4 桶并生效，剩 6 桶继续原联单
        register("I-070", leakBody("INC-1", 4, 40)).andExpect(status().isCreated());
        approveThree("I-070", "INC-1", "A", "2026-09-26T12");
        mockMvc.perform(get("/api/manifests/I-070/segments")).andExpect(jsonPath("$.length()").value(1));

        // 第二笔遗失只能在剩余 6 桶 / 60kg 内登记，超过则拒绝
        register("I-070", lossBodyAt("INC-2", 7, 70, "2026-09-26T12:30:00Z"))
                .andExpect(status().is(422));
        register("I-070", lossBodyAt("INC-2", 2, 20, "2026-09-26T12:30:00Z"))
                .andExpect(status().isCreated());
        approveThree("I-070", "INC-2", "B", "2026-09-26T13");
        decide("I-070", "INC-2", "ID-B-R", "REGULATOR", "APPROVED", "2026-09-26T13:20:00Z")
                .andExpect(jsonPath("$.status").value("EFFECTIVE"));

        // 两个段：一个新运输段 40，一个损失记录 20；原联单剩 4 桶 / 40
        mockMvc.perform(get("/api/manifests/I-070/segments"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("NEW_TRANSPORT"))
                .andExpect(jsonPath("$[1].type").value("LOSS"));
        mockMvc.perform(get("/api/manifests/I-070"))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.remainingDeclaredWeight").value(40.0));
    }

    // ---- 终止路径：不得留下半条运输链 ----

    @Test
    void rejectionReleasesFreezeAndCreatesNoSegment() throws Exception {
        inTransit("I-030");
        register("I-030", leakBody("INC-1", 4, 40)).andExpect(status().isCreated());
        decide("I-030", "INC-1", "ID-1", "GENERATOR", "REJECTED", "2026-09-26T12:00:00Z")
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/manifests/I-030/segments")).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/manifests/I-030"))
                .andExpect(jsonPath("$.packageFreezeActive").value(false));
        // 释放后正常交接可继续
        handover("I-030", "E-3", "TRANSPORTER", 100, "2026-09-26T13:00:00Z")
                .andExpect(jsonPath("$.status").value("RECEIVED_BY_TRANSPORTER"));
        // 终态后不能再决定
        decide("I-030", "INC-1", "ID-2", "TRANSPORTER", "APPROVED", "2026-09-26T13:10:00Z")
                .andExpect(status().is(422));
    }

    @Test
    void onlyTransporterCanWithdraw() throws Exception {
        inTransit("I-031");
        register("I-031", leakBody("INC-1", 4, 40)).andExpect(status().isCreated());
        decide("I-031", "INC-1", "ID-1", "GENERATOR", "WITHDRAWN", "2026-09-26T12:00:00Z")
                .andExpect(status().is(422));
        decide("I-031", "INC-1", "ID-2", "TRANSPORTER", "WITHDRAWN", "2026-09-26T12:00:00Z")
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        // 撤回释放名额，可登记新异常
        register("I-031", leakBody("INC-2", 2, 20)).andExpect(status().isCreated());
    }

    @Test
    void regulatoryFreezeTerminatesPendingIncident() throws Exception {
        inTransit("I-032");
        register("I-032", leakBody("INC-1", 4, 40)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/manifests/I-032/freeze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regulatoryFrozen").value(true));

        mockMvc.perform(get("/api/manifests/I-032/incidents/INC-1"))
                .andExpect(jsonPath("$.status").value("FROZEN"));
        mockMvc.perform(get("/api/manifests/I-032/segments")).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/manifests/I-032"))
                .andExpect(jsonPath("$.packageFreezeActive").value(false));

        // 冻结后不能再决定、不能登记新异常
        decide("I-032", "INC-1", "ID-1", "GENERATOR", "APPROVED", "2026-09-26T12:00:00Z")
                .andExpect(status().is(422));
        register("I-032", leakBody("INC-2", 2, 20)).andExpect(status().is(422));
    }

    @Test
    void oneRoleDecidesOnlyOnceAndTimeOrderEnforced() throws Exception {
        inTransit("I-033");
        register("I-033", leakBody("INC-1", 4, 40)).andExpect(status().isCreated());
        decide("I-033", "INC-1", "ID-1", "GENERATOR", "APPROVED", "2026-09-26T12:00:00Z")
                .andExpect(status().isOk());
        decide("I-033", "INC-1", "ID-2", "GENERATOR", "APPROVED", "2026-09-26T13:00:00Z")
                .andExpect(status().is(422));
        decide("I-033", "INC-1", "ID-3", "TRANSPORTER", "APPROVED", "2026-09-26T11:00:00Z")
                .andExpect(status().is(422));
    }

    // ---- 并发互斥（第 4 点） ----

    @Test
    void incidentGuardBlocksCorrectionOnSameManifest() throws Exception {
        // 已完成联单无法正常登记异常（状态限制），直接占用 INCIDENT 守卫模拟并发中的异常处置，
        // 此时更正创建必须被共享守卫拒绝。
        completed("I-040", 100);
        activeOps.save(new com.chris64233.cc.wastemanifest.manifest.ActiveManifestOp(
                "I-040", com.chris64233.cc.wastemanifest.manifest.ActiveOpType.INCIDENT,
                "INC-GUARD", 1));
        activeOps.flush();

        mockMvc.perform(post("/api/manifests/I-040/corrections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correctionNo": "CR-1", "applicantRole": "GENERATOR",
                                 "reason": "重量申报错误", "evidenceRef": "ev",
                                 "changes": [{"field": "WEIGHT", "itemSeq": 1, "newValue": "95"}]}
                                """))
                .andExpect(status().is(422));
    }

    // ---- 查询：证据、决定、拆分关系、完整交接链 ----

    @Test
    void recordShowsIncidentsDecisionsSplitsAndChains() throws Exception {
        inTransit("I-050");
        register("I-050", leakBody("INC-1", 4, 40)).andExpect(status().isCreated());
        decide("I-050", "INC-1", "ID-1", "GENERATOR", "APPROVED", "2026-09-26T12:00:00Z");
        decide("I-050", "INC-1", "ID-2", "TRANSPORTER", "APPROVED", "2026-09-26T12:05:00Z");
        decide("I-050", "INC-1", "ID-3", "OLD_DISPOSER", "APPROVED", "2026-09-26T12:10:00Z")
                .andExpect(jsonPath("$.status").value("EFFECTIVE"));
        segmentHandover("I-050", "INC-1-S1", "SE-1", "TRANSPORTER", 40, "2026-09-26T15:00:00Z");
        segmentHandover("I-050", "INC-1-S1", "SE-2", "DISPOSER", 40, "2026-09-26T16:00:00Z");

        mockMvc.perform(get("/api/manifests/I-050/record"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidents.length()").value(1))
                .andExpect(jsonPath("$.incidents[0].evidenceRef").value("photo-1"))
                .andExpect(jsonPath("$.incidents[0].location").value("G42 K120"))
                .andExpect(jsonPath("$.incidents[0].decisions.length()").value(3))
                .andExpect(jsonPath("$.incidents[0].affectedLines[0].affectedPackageCount").value(4))
                .andExpect(jsonPath("$.incidents[0].segments[0].items[0].sourceItemSeq").value(1))
                .andExpect(jsonPath("$.segments.length()").value(1))
                .andExpect(jsonPath("$.segments[0].handoverEvents.length()").value(2))
                .andExpect(jsonPath("$.timeline.length()").value(1));
    }

    @Test
    void incidentNotFound() throws Exception {
        inTransit("I-060");
        mockMvc.perform(get("/api/manifests/I-060/incidents/NOPE"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/manifests/I-060/segments/NOPE-S1"))
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
                                    {"wasteCategory": "HW08", "packageCount": 10, "declaredWeight": 100}
                                  ]
                                }
                                """.formatted(manifestNo)))
                .andExpect(status().isCreated());
    }

    /** 推进到运输中：产生方交运 → 承运方接货前（IN_TRANSIT）。 */
    private void inTransit(String manifestNo) throws Exception {
        createManifest(manifestNo);
        handover(manifestNo, "E-1", "GENERATOR", 100, "2026-09-26T08:00:00Z")
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
    }

    private void completed(String manifestNo, double receivedWeight) throws Exception {
        createManifest(manifestNo);
        handover(manifestNo, "E-1", "GENERATOR", 100, "2026-09-26T08:00:00Z");
        handover(manifestNo, "E-2", "TRANSPORTER", 100, "2026-09-26T09:00:00Z");
        handover(manifestNo, "E-3", "DISPOSER", receivedWeight, "2026-09-26T10:00:00Z")
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

    private ResultActions segmentHandover(String manifestNo, String segmentNo, String eventNo,
                                          String role, double weight, String occurredAt) throws Exception {
        return mockMvc.perform(post("/api/manifests/{no}/segments/{sno}/handover", manifestNo, segmentNo)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"eventNo": "%s", "role": "%s", "weight": %s, "occurredAt": "%s"}
                        """.formatted(eventNo, role, weight, occurredAt)));
    }

    private String leakBody(String incidentNo, int packages, double weight) {
        return leakBodyAt(incidentNo, packages, weight, "2026-09-26T11:00:00Z");
    }

    private String leakBodyAt(String incidentNo, int packages, double weight, String occurredAt) {
        return """
                {"incidentNo": "%s", "type": "LEAK",
                 "occurredAt": "%s", "location": "G42 K120", "evidenceRef": "photo-1",
                 "affectedLines": [{"itemSeq": 1, "packageCount": %d, "weight": %s}]}
                """.formatted(incidentNo, occurredAt, packages, weight);
    }

    private String lossBody(String incidentNo, int packages, double weight) {
        return lossBodyAt(incidentNo, packages, weight, "2026-09-26T11:00:00Z");
    }

    private String lossBodyAt(String incidentNo, int packages, double weight, String occurredAt) {
        return """
                {"incidentNo": "%s", "type": "LOSS",
                 "occurredAt": "%s", "location": "G42 K120",
                 "evidenceRef": "police-report-1",
                 "affectedLines": [{"itemSeq": 1, "packageCount": %d, "weight": %s}]}
                """.formatted(incidentNo, occurredAt, packages, weight);
    }

    /** 产生方、承运方、原处置方依次批准。 */
    private void approveThree(String manifestNo, String incidentNo, String tag, String hour)
            throws Exception {
        decide(manifestNo, incidentNo, "ID-G-" + tag, "GENERATOR", "APPROVED", hour + ":00:00Z");
        decide(manifestNo, incidentNo, "ID-T-" + tag, "TRANSPORTER", "APPROVED", hour + ":05:00Z");
        decide(manifestNo, incidentNo, "ID-D-" + tag, "OLD_DISPOSER", "APPROVED", hour + ":10:00Z");
    }

    private String diversionBody(String incidentNo, int packages, double weight) {
        return """
                {"incidentNo": "%s", "type": "DIVERSION",
                 "occurredAt": "2026-09-26T11:00:00Z", "location": "G42 分流点",
                 "evidenceRef": "notice-1",
                 "affectedLines": [{"itemSeq": 1, "packageCount": %d, "weight": %s}],
                 "newDisposerId": "disp-2",
                 "estimatedArrivalAt": "2026-09-26T18:00:00Z"}
                """.formatted(incidentNo, packages, weight);
    }

    private ResultActions register(String manifestNo, String body) throws Exception {
        return mockMvc.perform(post("/api/manifests/{no}/incidents", manifestNo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions decide(String manifestNo, String incidentNo, String decisionNo,
                                 String role, String value, String occurredAt) throws Exception {
        return mockMvc.perform(post("/api/manifests/{no}/incidents/{ino}/decisions",
                        manifestNo, incidentNo)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"decisionNo": "%s", "role": "%s", "value": "%s", "occurredAt": "%s"}
                        """.formatted(decisionNo, role, value, occurredAt)));
    }
}
