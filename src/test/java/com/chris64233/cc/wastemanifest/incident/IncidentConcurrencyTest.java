package com.chris64233.cc.wastemanifest.incident;

import com.chris64233.cc.wastemanifest.correction.CorrectionDecisionRepository;
import com.chris64233.cc.wastemanifest.correction.CorrectionRepository;
import com.chris64233.cc.wastemanifest.correction.ManifestVersionRepository;
import com.chris64233.cc.wastemanifest.incident.dto.IncidentDecisionRequest;
import com.chris64233.cc.wastemanifest.incident.dto.RegisterIncidentRequest;
import com.chris64233.cc.wastemanifest.incident.dto.AffectedLineRequest;
import com.chris64233.cc.wastemanifest.manifest.ActiveManifestOpRepository;
import com.chris64233.cc.wastemanifest.manifest.ManifestEventRepository;
import com.chris64233.cc.wastemanifest.manifest.ManifestFlowAdjustmentRepository;
import com.chris64233.cc.wastemanifest.manifest.ManifestRepository;
import com.chris64233.cc.wastemanifest.manifest.ManifestService;
import com.chris64233.cc.wastemanifest.manifest.CustodianRole;
import com.chris64233.cc.wastemanifest.manifest.dto.CreateManifestRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.HandoverRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestItemRequest;
import com.chris64233.cc.wastemanifest.manifest.exception.BusinessRuleException;
import com.chris64233.cc.wastemanifest.manifest.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IncidentConcurrencyTest {

    @Autowired
    private ManifestService manifestService;

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
    private ManifestFlowAdjustmentRepository flowAdjustments;

    @Autowired
    private ActiveManifestOpRepository activeOps;

    @BeforeEach
    void setUp() {
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

    @Test
    void concurrentRegistrationsAllowExactlyOneActiveIncident() throws Exception {
        inTransit("IC-001");

        AtomicInteger success = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        runConcurrently(6, () -> {
            String no = "INC-" + Thread.currentThread().getName();
            try {
                incidentService.register("IC-001", leak(no, 4, 40));
                success.incrementAndGet();
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        assertThat(success.get()).isEqualTo(1);
        assertThat(failures).hasSize(5);
        assertThat(failures).allMatch(t -> t instanceof BusinessRuleException
                || t instanceof ConflictException);
        assertThat(incidents.findAll())
                .filteredOn(i -> i.getStatus() == IncidentStatus.PENDING)
                .hasSize(1);
        assertThat(activeOps.findAll()).hasSize(1);
    }

    @Test
    void concurrentFinalDecisionsMakeExactlyOneEffective() throws Exception {
        inTransit("IC-002");
        incidentService.register("IC-002", leak("INC-1", 4, 40));
        incidentService.decide("IC-002", "INC-1", new IncidentDecisionRequest(
                "ID-1", IncidentRole.GENERATOR, IncidentDecisionValue.APPROVED,
                Instant.parse("2026-09-26T12:00:00Z")));
        incidentService.decide("IC-002", "INC-1", new IncidentDecisionRequest(
                "ID-2", IncidentRole.TRANSPORTER, IncidentDecisionValue.APPROVED,
                Instant.parse("2026-09-26T12:05:00Z")));

        // 原处置方的最终批准并发提交：恰一笔生效，只产生一个运输段、一组拆减。
        AtomicInteger success = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        runConcurrently(6, () -> {
            String no = "ID-FINAL-" + Thread.currentThread().getName();
            try {
                incidentService.decide("IC-002", "INC-1", new IncidentDecisionRequest(
                        no, IncidentRole.OLD_DISPOSER, IncidentDecisionValue.APPROVED,
                        Instant.parse("2026-09-26T12:10:00Z")));
                success.incrementAndGet();
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        assertThat(success.get()).isEqualTo(1);
        assertThat(failures).hasSize(5);
        assertThat(failures).allMatch(t -> t instanceof BusinessRuleException
                || t instanceof ConflictException);
        TransportIncident incident = incidents.findByIncidentNo("INC-1").orElseThrow();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.EFFECTIVE);
        assertThat(segments.findByParentManifestManifestNoOrderBySeqNoAsc("IC-002")).hasSize(1);
        assertThat(flowAdjustments.findByManifestManifestNo("IC-002"))
                .hasSize(1)
                .first()
                .satisfies(a -> {
                    assertThat(a.getDetachedPackageCount()).isEqualTo(4);
                    assertThat(a.getDetachedWeight()).isEqualByComparingTo("40");
                });
        assertThat(freezes.findByManifestManifestNoAndStatus(
                "IC-002", PackageFreezeStatus.FROZEN)).isEmpty();
        assertThat(activeOps.findAll()).isEmpty();
    }

    @Test
    void concurrentSplitAndHandoverNeverLeavesContradiction() throws Exception {
        // 最终决定（一次性拆分、释放冻结）与承运方接货并发：行锁串行化，
        // 交接要么被冻结拦截，要么在拆分后按剩余口径成功，二者必居其一且状态自洽。
        inTransit("IC-003");
        incidentService.register("IC-003", leak("INC-1", 4, 40));
        incidentService.decide("IC-003", "INC-1", new IncidentDecisionRequest(
                "ID-1", IncidentRole.GENERATOR, IncidentDecisionValue.APPROVED,
                Instant.parse("2026-09-26T12:00:00Z")));
        incidentService.decide("IC-003", "INC-1", new IncidentDecisionRequest(
                "ID-2", IncidentRole.TRANSPORTER, IncidentDecisionValue.APPROVED,
                Instant.parse("2026-09-26T12:05:00Z")));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        Thread decider = new Thread(() -> {
            ready.countDown();
            try {
                start.await();
                incidentService.decide("IC-003", "INC-1", new IncidentDecisionRequest(
                        "ID-3", IncidentRole.OLD_DISPOSER, IncidentDecisionValue.APPROVED,
                        Instant.parse("2026-09-26T12:10:00Z")));
            } catch (Throwable t) {
                failures.add(t);
            } finally {
                done.countDown();
            }
        }, "decider");
        Thread receiver = new Thread(() -> {
            ready.countDown();
            try {
                start.await();
                // 承运方按未受影响的 60 接货；若先于冻结放行则可能成功，否则被 422 拦截
                manifestService.handover("IC-003", new HandoverRequest("E-3",
                        CustodianRole.TRANSPORTER, new BigDecimal("60"),
                        Instant.parse("2026-09-26T12:10:00Z")));
            } catch (Throwable t) {
                failures.add(t);
            } finally {
                done.countDown();
            }
        }, "receiver");
        decider.start();
        receiver.start();
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

        // 决定必定成功；交接要么成功（剩余口径 60），要么因冻结被拒。
        assertThat(incidents.findByIncidentNo("INC-1").orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.EFFECTIVE);
        assertThat(segments.findByParentManifestManifestNoOrderBySeqNoAsc("IC-003")).hasSize(1);
        var status = manifests.findByManifestNo("IC-003").orElseThrow().getStatus();
        assertThat(status).isIn(com.chris64233.cc.wastemanifest.manifest.ManifestStatus.IN_TRANSIT,
                com.chris64233.cc.wastemanifest.manifest.ManifestStatus.RECEIVED_BY_TRANSPORTER);
        // 无论谁先，冻结最终都已释放，且不存在半拆状态
        assertThat(freezes.findByManifestManifestNoAndStatus(
                "IC-003", PackageFreezeStatus.FROZEN)).isEmpty();
    }

    private void inTransit(String manifestNo) {
        manifestService.create(new CreateManifestRequest(manifestNo, "gen-1", "trans-1", "disp-1",
                List.of(new ManifestItemRequest("HW08", 10, new BigDecimal("100")))));
        manifestService.handover(manifestNo, new HandoverRequest("E-1", CustodianRole.GENERATOR,
                new BigDecimal("100"), Instant.parse("2026-09-26T08:00:00Z")));
    }

    private RegisterIncidentRequest leak(String incidentNo, int packages, double weight) {
        return new RegisterIncidentRequest(incidentNo, IncidentType.LEAK,
                Instant.parse("2026-09-26T11:00:00Z"), "G42 K120", "photo-1",
                List.of(new AffectedLineRequest(1, packages, new BigDecimal(String.valueOf(weight)))),
                null, null, null);
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
