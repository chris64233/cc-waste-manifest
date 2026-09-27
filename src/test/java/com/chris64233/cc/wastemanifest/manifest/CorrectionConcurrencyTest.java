package com.chris64233.cc.wastemanifest.manifest;

import com.chris64233.cc.wastemanifest.manifest.dto.CorrectionChangeRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.CorrectionDecisionRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.CreateCorrectionRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.CreateManifestRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.HandoverRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestItemRequest;
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
    private ManifestCorrectionRepository corrections;

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
    void concurrentCorrectionCreationKeepsExactlyOneActive() throws Exception {
        completeManifest("CC-101");

        AtomicInteger success = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        runConcurrently(8, () -> {
            String correctionNo = "CR-" + Thread.currentThread().getName();
            try {
                correctionService.createCorrection("CC-101", correctionRequest(correctionNo, "140"));
                success.incrementAndGet();
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        assertThat(success.get()).isEqualTo(1);
        assertThat(failures).hasSize(7);
        assertThat(failures).allMatch(t -> t instanceof ConflictException);
        assertThat(corrections.findByManifestManifestNoOrderByIdAsc("CC-101")).hasSize(1);
    }

    @Test
    void concurrentIdenticalDecisionsReplayIdempotently() throws Exception {
        completeManifest("CC-102");
        correctionService.createCorrection("CC-102", correctionRequest("CR-1", "140"));
        CorrectionDecisionRequest request = new CorrectionDecisionRequest("CD-1", ApproverRole.GENERATOR,
                DecisionType.APPROVE, Instant.parse("2026-09-27T08:00:00Z"));

        AtomicInteger success = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        runConcurrently(8, () -> {
            try {
                correctionService.decide("CC-102", "CR-1", request);
                success.incrementAndGet();
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        assertThat(success.get()).isEqualTo(8);
        assertThat(failures).isEmpty();
        var correction = corrections.findByCorrectionNo("CR-1").orElseThrow();
        assertThat(decisions.findByCorrectionOrderByIdAsc(correction)).hasSize(1);
        assertThat(correction.getStatus()).isEqualTo(CorrectionStatus.PENDING);
    }

    @Test
    void concurrentFinalApprovalsApplyCorrectionExactlyOnce() throws Exception {
        completeManifest("CC-103");
        correctionService.createCorrection("CC-103", correctionRequest("CR-1", "140"));
        correctionService.decide("CC-103", "CR-1", new CorrectionDecisionRequest("CD-1",
                ApproverRole.GENERATOR, DecisionType.APPROVE, Instant.parse("2026-09-27T08:00:00Z")));

        AtomicInteger success = new AtomicInteger();
        runConcurrently(6, () -> {
            String decisionNo = "CD-F-" + Thread.currentThread().getName();
            try {
                correctionService.decide("CC-103", "CR-1", new CorrectionDecisionRequest(decisionNo,
                        ApproverRole.DISPOSER, DecisionType.APPROVE, Instant.parse("2026-09-27T09:00:00Z")));
                success.incrementAndGet();
            } catch (Throwable ignored) {
            }
        });

        assertThat(success.get()).isGreaterThanOrEqualTo(1);
        var correction = corrections.findByCorrectionNo("CR-1").orElseThrow();
        assertThat(correction.getStatus()).isEqualTo(CorrectionStatus.APPLIED);
        assertThat(correction.getResultVersionNo()).isEqualTo(2);
        assertThat(manifestService.getDetail("CC-103").currentVersionNo()).isEqualTo(2);
        assertThat(versions.findByManifestManifestNoOrderByVersionNoAsc("CC-103")).hasSize(2);
    }

    private void completeManifest(String manifestNo) {
        manifestService.create(new CreateManifestRequest(manifestNo, "gen-1", "trans-1", "disp-1",
                List.of(new ManifestItemRequest("HW08", 10, new BigDecimal("100")),
                        new ManifestItemRequest("HW49", 5, new BigDecimal("50")))));
        manifestService.handover(manifestNo, new HandoverRequest("E-1", CustodianRole.GENERATOR,
                new BigDecimal("150"), Instant.parse("2026-09-26T08:00:00Z")));
        manifestService.handover(manifestNo, new HandoverRequest("E-2", CustodianRole.TRANSPORTER,
                new BigDecimal("150"), Instant.parse("2026-09-26T09:00:00Z")));
        manifestService.handover(manifestNo, new HandoverRequest("E-3", CustodianRole.DISPOSER,
                new BigDecimal("150"), Instant.parse("2026-09-26T10:00:00Z")));
    }

    private static CreateCorrectionRequest correctionRequest(String correctionNo, String newWeight) {
        return new CreateCorrectionRequest(correctionNo, "申报重量填写错误", "称重单 WS-1001",
                List.of(new CorrectionChangeRequest(0, CorrectionField.WEIGHT, newWeight)));
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
