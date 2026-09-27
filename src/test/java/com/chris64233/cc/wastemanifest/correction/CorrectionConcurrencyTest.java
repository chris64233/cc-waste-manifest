package com.chris64233.cc.wastemanifest.correction;

import com.chris64233.cc.wastemanifest.correction.dto.CorrectionDecisionRequest;
import com.chris64233.cc.wastemanifest.correction.dto.CreateCorrectionRequest;
import com.chris64233.cc.wastemanifest.correction.dto.FieldChangeRequest;
import com.chris64233.cc.wastemanifest.manifest.Manifest;
import com.chris64233.cc.wastemanifest.manifest.ManifestEventRepository;
import com.chris64233.cc.wastemanifest.manifest.ManifestRepository;
import com.chris64233.cc.wastemanifest.manifest.ManifestService;
import com.chris64233.cc.wastemanifest.manifest.CustodianRole;
import com.chris64233.cc.wastemanifest.manifest.dto.CreateManifestRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.DisputeConfirmRequest;
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
class CorrectionConcurrencyTest {

    @Autowired
    private ManifestService manifestService;

    @Autowired
    private CorrectionService correctionService;

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

    @BeforeEach
    void setUp() {
        decisions.deleteAll();
        corrections.deleteAll();
        versions.deleteAll();
        events.deleteAll();
        manifests.deleteAll();
    }

    @Test
    void concurrentFinalDecisionsMakeExactlyOneEffective() throws Exception {
        completeManifest("CC-001", new BigDecimal("100"));
        correctionService.createCorrection("CC-001", weightCorrection("CR-1", "CE-REQ-1"));
        correctionService.decide("CC-001", "CR-1", new CorrectionDecisionRequest(
                "CD-1", DecisionRole.GENERATOR, DecisionValue.APPROVED,
                Instant.parse("2026-09-27T08:00:00Z")));

        // 产生方已批准，处置方的最终批准并发提交：恰好一笔生效，其余失败，只产生 v2。
        AtomicInteger success = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        runConcurrently(6, () -> {
            String no = "CD-FINAL-" + Thread.currentThread().getName();
            try {
                correctionService.decide("CC-001", "CR-1", new CorrectionDecisionRequest(
                        no, DecisionRole.DISPOSER, DecisionValue.APPROVED,
                        Instant.parse("2026-09-27T09:00:00Z")));
                success.incrementAndGet();
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        assertThat(success.get()).isEqualTo(1);
        assertThat(failures).hasSize(5);
        assertThat(failures).allMatch(t -> t instanceof BusinessRuleException
                || t instanceof ConflictException);
        Correction cr = corrections.findByCorrectionNo("CR-1").orElseThrow();
        assertThat(cr.getStatus()).isEqualTo(CorrectionStatus.EFFECTIVE);
        assertThat(cr.getResultVersionNo()).isEqualTo(2);
        assertThat(versions.findByManifestNoOrderByVersionNoAsc("CC-001")).hasSize(2);
        assertThat(manifests.findByManifestNo("CC-001").orElseThrow().getCurrentVersionNo())
                .isEqualTo(2);
    }

    @Test
    void concurrentCorrectionCreatesAllowExactlyOneActive() throws Exception {
        completeManifest("CC-002", new BigDecimal("100"));

        AtomicInteger success = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        runConcurrently(6, () -> {
            String no = "CR-" + Thread.currentThread().getName();
            try {
                correctionService.createCorrection("CC-002", weightCorrection(no, "REQ-" + no));
                success.incrementAndGet();
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        assertThat(success.get()).isEqualTo(1);
        assertThat(failures).hasSize(5);
        assertThat(corrections.findAll())
                .filteredOn(c -> c.getStatus() == CorrectionStatus.PENDING)
                .hasSize(1);
    }

    /**
     * 防御纵深：即使绕过活动更正唯一约束注入一笔基于 v1 的 PENDING 更正，
     * 在 v2 生效后它也必须被标记 SUPERSEDED、不得落地。
     */
    @Test
    void staleInjectedCorrectionIsSupersededAndCannotLand() {
        completeManifest("CC-003", new BigDecimal("100"));
        Manifest manifest = manifests.findByManifestNo("CC-003").orElseThrow();

        correctionService.createCorrection("CC-003", weightCorrection("CR-1", "REQ-1"));
        correctionService.decide("CC-003", "CR-1", new CorrectionDecisionRequest(
                "CD-1", DecisionRole.GENERATOR, DecisionValue.APPROVED,
                Instant.parse("2026-09-27T08:00:00Z")));
        correctionService.decide("CC-003", "CR-1", new CorrectionDecisionRequest(
                "CD-2", DecisionRole.DISPOSER, DecisionValue.APPROVED,
                Instant.parse("2026-09-27T09:00:00Z")));
        assertThat(manifests.findByManifestNo("CC-003").orElseThrow().getCurrentVersionNo())
                .isEqualTo(2);

        // 注入一笔活动守卫列为空（绕过唯一约束）但状态为 PENDING、基线仍为 v1 的更正
        Correction stale = new Correction("CR-STALE", manifest, 1, DecisionRole.GENERATOR,
                "注入的过期更正", "evidence", DisputeImpact.NONE);
        ReflectionTestUtils.setField(stale, "activeManifestNo", null);
        corrections.saveAndFlush(stale);

        assertThatThrownBy(() -> correctionService.decide("CC-003", "CR-STALE",
                new CorrectionDecisionRequest("CD-3", DecisionRole.GENERATOR,
                        DecisionValue.APPROVED, Instant.parse("2026-09-27T10:00:00Z"))))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(corrections.findByCorrectionNo("CR-STALE").orElseThrow().getStatus())
                .isEqualTo(CorrectionStatus.SUPERSEDED);
        assertThat(versions.findByManifestNoOrderByVersionNoAsc("CC-003")).hasSize(2);
        assertThat(manifests.findByManifestNo("CC-003").orElseThrow().getCurrentVersionNo())
                .isEqualTo(2);
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

    private CreateCorrectionRequest weightCorrection(String correctionNo, String evidenceRef) {
        return new CreateCorrectionRequest(correctionNo, DecisionRole.GENERATOR,
                "重量申报错误", evidenceRef,
                List.of(new FieldChangeRequest(CorrectionField.WEIGHT, 1, "95")));
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
