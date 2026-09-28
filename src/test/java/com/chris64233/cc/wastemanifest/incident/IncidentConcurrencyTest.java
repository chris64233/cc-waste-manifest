package com.chris64233.cc.wastemanifest.incident;

import com.chris64233.cc.wastemanifest.correction.CorrectionDecisionRepository;
import com.chris64233.cc.wastemanifest.correction.CorrectionField;
import com.chris64233.cc.wastemanifest.correction.CorrectionRepository;
import com.chris64233.cc.wastemanifest.correction.CorrectionStatus;
import com.chris64233.cc.wastemanifest.correction.CorrectionService;
import com.chris64233.cc.wastemanifest.correction.DecisionRole;
import com.chris64233.cc.wastemanifest.correction.DecisionValue;
import com.chris64233.cc.wastemanifest.correction.ManifestVersionRepository;
import com.chris64233.cc.wastemanifest.correction.dto.CorrectionDecisionRequest;
import com.chris64233.cc.wastemanifest.correction.dto.CreateCorrectionRequest;
import com.chris64233.cc.wastemanifest.correction.dto.FieldChangeRequest;
import com.chris64233.cc.wastemanifest.incident.dto.AffectedPackageRequest;
import com.chris64233.cc.wastemanifest.incident.dto.CreatePlanRequest;
import com.chris64233.cc.wastemanifest.incident.dto.PlanDecisionRequest;
import com.chris64233.cc.wastemanifest.incident.dto.PlanResponse;
import com.chris64233.cc.wastemanifest.incident.dto.RegisterIncidentRequest;
import com.chris64233.cc.wastemanifest.manifest.CustodianRole;
import com.chris64233.cc.wastemanifest.manifest.Manifest;
import com.chris64233.cc.wastemanifest.manifest.ManifestEventRepository;
import com.chris64233.cc.wastemanifest.manifest.ManifestRepository;
import com.chris64233.cc.wastemanifest.manifest.ManifestService;
import com.chris64233.cc.wastemanifest.manifest.dto.CreateManifestRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.HandoverRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestItemRequest;
import com.chris64233.cc.wastemanifest.manifest.exception.BusinessRuleException;
import com.chris64233.cc.wastemanifest.manifest.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IncidentConcurrencyTest {

    @Autowired
    private ManifestService manifestService;
    @Autowired
    private CorrectionService correctionService;
    @Autowired
    private IncidentService incidentService;

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

    @BeforeEach
    void setUp() {
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
    void concurrentFinalPlanDecisionsMakeExactlyOneEffectiveWithoutHalfChain() throws Exception {
        moveToTransporter("IC-001");
        incidentService.register("IC-001", new RegisterIncidentRequest(
                "IE-1", IncidentType.LEAK, Instant.parse("2026-09-26T09:30:00Z"),
                "K120", "photo", List.of(new AffectedPackageRequest(1, 3))));
        incidentService.createPlan("IC-001", "IE-1",
                new CreatePlanRequest("PL-1", null, null, true, false));
        incidentService.decide("IC-001", "IE-1", "PL-1", new PlanDecisionRequest(
                "PD-G", PlanRole.GENERATOR, DecisionValue.APPROVED,
                Instant.parse("2026-09-26T10:00:00Z")));
        incidentService.decide("IC-001", "IE-1", "PL-1", new PlanDecisionRequest(
                "PD-T", PlanRole.TRANSPORTER, DecisionValue.APPROVED,
                Instant.parse("2026-09-26T11:00:00Z")));
        incidentService.decide("IC-001", "IE-1", "PL-1", new PlanDecisionRequest(
                "PD-O", PlanRole.OLD_DISPOSER, DecisionValue.APPROVED,
                Instant.parse("2026-09-26T12:00:00Z")));

        // 监管最终决定并发提交：恰好一人生效，其余失败；只产生一份损失记录与拆分。
        AtomicInteger success = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        runConcurrently(6, () -> {
            String no = "PD-R-" + Thread.currentThread().getName();
            try {
                incidentService.decide("IC-001", "IE-1", "PL-1", new PlanDecisionRequest(
                        no, PlanRole.REGULATOR, DecisionValue.APPROVED,
                        Instant.parse("2026-09-26T13:00:00Z")));
                success.incrementAndGet();
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        assertThat(success.get()).isEqualTo(1);
        assertThat(failures).hasSize(5);
        assertThat(failures).allMatch(t -> t instanceof BusinessRuleException
                || t instanceof ConflictException);
        assertThat(plans.findByPlanNo("PL-1").orElseThrow().getStatus())
                .isEqualTo(PlanStatus.EFFECTIVE);
        assertThat(incidents.findByEventNo("IE-1").orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.RESOLVED);
        assertThat(losses.findAll()).hasSize(1);
        assertThat(segments.findAll()).isEmpty();
        List<IncidentPackageSplit> rows = splits.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAffectedQuantity()).isEqualTo(3);
        assertThat(rows.get(0).getUnaffectedQuantity()).isEqualTo(7);
    }

    @Test
    void concurrentPlanCreatesAllowExactlyOnePending() throws Exception {
        moveToTransporter("IC-002");
        incidentService.register("IC-002", new RegisterIncidentRequest(
                "IE-2", IncidentType.LOSS, Instant.parse("2026-09-26T09:30:00Z"),
                "S20", "police", List.of(new AffectedPackageRequest(1, 2))));

        AtomicInteger success = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        runConcurrently(6, () -> {
            String no = "PL-" + Thread.currentThread().getName();
            try {
                incidentService.createPlan("IC-002", "IE-2",
                        new CreatePlanRequest(no, null, null, true, false));
                success.incrementAndGet();
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        assertThat(success.get()).isEqualTo(1);
        assertThat(failures).hasSize(5);
        assertThat(plans.findAll()).filteredOn(IncidentPlan::isPending).hasSize(1);
        assertThat(splits.findAll()).isEmpty();
        assertThat(losses.findAll()).isEmpty();
    }

    /**
     * 防御纵深：即使绕过正常状态机注入一笔基于 v1 的 PENDING 方案，
     * 在版本推进到 v2 后它也必须被标记 SUPERSEDED、不得落地、不留半条运输链。
     */
    @Test
    void staleInjectedPlanIsSupersededAndCannotLand() {
        completeManifest("IC-003", new BigDecimal("100"));
        // 更正生效，版本推进到 v2
        correctionService.createCorrection("IC-003", new CreateCorrectionRequest(
                "CR-1", DecisionRole.GENERATOR, "重量错误", "ev",
                List.of(new FieldChangeRequest(CorrectionField.WEIGHT, 1, "95"))));
        correctionService.decide("IC-003", "CR-1", new CorrectionDecisionRequest(
                "CD-1", DecisionRole.GENERATOR, DecisionValue.APPROVED,
                Instant.parse("2026-09-27T08:00:00Z")));
        correctionService.decide("IC-003", "CR-1", new CorrectionDecisionRequest(
                "CD-2", DecisionRole.DISPOSER, DecisionValue.APPROVED,
                Instant.parse("2026-09-27T09:00:00Z")));
        assertThat(manifests.findByManifestNo("IC-003").orElseThrow().getCurrentVersionNo())
                .isEqualTo(2);

        Manifest manifest = manifests.findByManifestNo("IC-003").orElseThrow();
        Incident injected = new Incident("IE-STALE", manifest, IncidentType.LEAK,
                Instant.parse("2026-09-26T09:30:00Z"), "road", "ev", 1);
        injected.addAffectedPackage(new IncidentAffectedPackage(injected, 1, "HW08", 3));
        incidents.saveAndFlush(injected);
        IncidentPlan stale = new IncidentPlan("PL-STALE", injected, 1, null, null, true, false);
        ReflectionTestUtils.setField(stale, "activeIncidentId", injected.getId());
        plans.saveAndFlush(stale);

        assertThatThrownBy(() -> incidentService.decide("IC-003", "IE-STALE", "PL-STALE",
                new PlanDecisionRequest("PD-3", PlanRole.GENERATOR, DecisionValue.APPROVED,
                        Instant.parse("2026-09-27T10:00:00Z"))))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(plans.findByPlanNo("PL-STALE").orElseThrow().getStatus())
                .isEqualTo(PlanStatus.SUPERSEDED);
        assertThat(splits.findAll()).isEmpty();
        assertThat(losses.findAll()).isEmpty();
        assertThat(segments.findAll()).isEmpty();
        assertThat(incidents.findByEventNo("IE-STALE").orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.OPEN);
    }

    @Test
    void rejectedPlanPersistsDecisionButLeavesNoChainAndIncidentStaysOpen() {
        moveToTransporter("IC-004");
        incidentService.register("IC-004", new RegisterIncidentRequest(
                "IE-4", IncidentType.DIVERSION, Instant.parse("2026-09-26T09:30:00Z"),
                "K300", "order", List.of(new AffectedPackageRequest(1, 4))));
        incidentService.createPlan("IC-004", "IE-4", new CreatePlanRequest(
                "PL-4", "disp-2", Instant.parse("2026-09-27T08:00:00Z"), false, false));

        PlanResponse response = incidentService.decide("IC-004", "IE-4", "PL-4",
                new PlanDecisionRequest("PD-1", PlanRole.OLD_DISPOSER, DecisionValue.REJECTED,
                        Instant.parse("2026-09-26T10:00:00Z")));
        assertThat(response.status()).isEqualTo(PlanStatus.REJECTED);

        assertThat(plans.findByPlanNo("PL-4").orElseThrow().getStatus())
                .isEqualTo(PlanStatus.REJECTED);
        // 拒绝决定事件落库保留
        assertThat(decisions.findAll()).hasSize(1);
        // 不留半条运输链，异常仍 OPEN、包装仍冻结
        assertThat(segments.findAll()).isEmpty();
        assertThat(splits.findAll()).isEmpty();
        assertThat(incidents.findByEventNo("IE-4").orElseThrow().isOpen()).isTrue();
    }

    private void moveToTransporter(String manifestNo) {
        manifestService.create(new CreateManifestRequest(manifestNo, "gen-1", "trans-1", "disp-1",
                List.of(new ManifestItemRequest("HW08", 10, new BigDecimal("100")),
                        new ManifestItemRequest("HW49", 5, new BigDecimal("50")))));
        manifestService.handover(manifestNo, new HandoverRequest("E-1", CustodianRole.GENERATOR,
                new BigDecimal("150"), Instant.parse("2026-09-26T08:00:00Z")));
        manifestService.handover(manifestNo, new HandoverRequest("E-2", CustodianRole.TRANSPORTER,
                new BigDecimal("150"), Instant.parse("2026-09-26T09:00:00Z")));
    }

    private void completeManifest(String manifestNo, BigDecimal receivedWeight) {
        manifestService.create(new CreateManifestRequest(manifestNo, "gen-1", "trans-1", "disp-1",
                List.of(new ManifestItemRequest("HW08", 10, new BigDecimal("100")))));
        manifestService.handover(manifestNo, new HandoverRequest("E-1", CustodianRole.GENERATOR,
                new BigDecimal("100"), Instant.parse("2026-09-26T08:00:00Z")));
        manifestService.handover(manifestNo, new HandoverRequest("E-2", CustodianRole.TRANSPORTER,
                new BigDecimal("100"), Instant.parse("2026-09-26T09:00:00Z")));
        manifestService.handover(manifestNo, new HandoverRequest("E-3", CustodianRole.DISPOSER,
                receivedWeight, Instant.parse("2026-09-26T10:00:00Z")));
    }

    private void runConcurrently(int threads, Runnable task) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "worker-" + i);
            thread.start();
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
    }
}
