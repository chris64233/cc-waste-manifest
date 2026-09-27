package com.chris64233.cc.wastemanifest.manifest;

import com.chris64233.cc.wastemanifest.manifest.dto.CreateManifestRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.DisputeConfirmRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.HandoverRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestItemRequest;
import com.chris64233.cc.wastemanifest.manifest.exception.BusinessRuleException;
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
class ManifestConcurrencyTest {

    @Autowired
    private ManifestService service;

    @Autowired
    private ManifestRepository manifests;

    @Autowired
    private ManifestEventRepository events;

    @Autowired
    private com.chris64233.cc.wastemanifest.correction.CorrectionDecisionRepository correctionDecisions;

    @Autowired
    private com.chris64233.cc.wastemanifest.correction.CorrectionRepository correctionRepo;

    @Autowired
    private com.chris64233.cc.wastemanifest.correction.ManifestVersionRepository versionRepo;

    @BeforeEach
    void setUp() {
        correctionDecisions.deleteAll();
        correctionRepo.deleteAll();
        versionRepo.deleteAll();
        events.deleteAll();
        manifests.deleteAll();
    }

    @Test
    void concurrentIdenticalHandoverReplaysIdempotently() throws Exception {
        createManifest("C-001");
        HandoverRequest request = new HandoverRequest("CE-1", CustodianRole.GENERATOR,
                new BigDecimal("100"), Instant.parse("2026-09-26T08:00:00Z"));

        AtomicInteger success = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        runConcurrently(8, () -> {
            try {
                service.handover("C-001", request);
                success.incrementAndGet();
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        assertThat(success.get()).isEqualTo(8);
        assertThat(failures).isEmpty();
        assertThat(events.findByManifestManifestNoOrderByIdAsc("C-001")).hasSize(1);
        assertThat(service.getDetail("C-001").status()).isEqualTo(ManifestStatus.IN_TRANSIT);
    }

    @Test
    void concurrentCompetingHandoverAllowsExactlyOneWinner() throws Exception {
        createManifest("C-002");
        Instant occurredAt = Instant.parse("2026-09-26T08:00:00Z");

        AtomicInteger success = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        runConcurrently(8, () -> {
            String eventNo = Thread.currentThread().getName();
            try {
                service.handover("C-002",
                        new HandoverRequest(eventNo, CustodianRole.GENERATOR, new BigDecimal("100"), occurredAt));
                success.incrementAndGet();
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        assertThat(success.get()).isEqualTo(1);
        assertThat(failures).hasSize(7);
        assertThat(failures).allMatch(t -> t instanceof BusinessRuleException);
        assertThat(events.findByManifestManifestNoOrderByIdAsc("C-002")).hasSize(1);
        assertThat(service.getDetail("C-002").status()).isEqualTo(ManifestStatus.IN_TRANSIT);
    }

    @Test
    void concurrentContradictoryDisputeConfirmationsStayDisputed() throws Exception {
        createManifest("C-003");
        service.handover("C-003", new HandoverRequest("CE-1", CustodianRole.GENERATOR,
                new BigDecimal("100"), Instant.parse("2026-09-26T08:00:00Z")));
        service.handover("C-003", new HandoverRequest("CE-2", CustodianRole.TRANSPORTER,
                new BigDecimal("100"), Instant.parse("2026-09-26T09:00:00Z")));
        service.handover("C-003", new HandoverRequest("CE-3", CustodianRole.DISPOSER,
                new BigDecimal("80"), Instant.parse("2026-09-26T10:00:00Z")));
        assertThat(service.getDetail("C-003").status()).isEqualTo(ManifestStatus.WEIGHT_DISPUTE);

        AtomicInteger success = new AtomicInteger();
        runConcurrently(2, () -> {
            boolean generator = Thread.currentThread().getName().endsWith("0");
            try {
                service.confirmDispute("C-003", new DisputeConfirmRequest(
                        generator ? "CE-4" : "CE-5",
                        generator ? CustodianRole.GENERATOR : CustodianRole.DISPOSER,
                        generator ? new BigDecimal("90") : new BigDecimal("85"),
                        Instant.parse("2026-09-26T11:00:00Z")));
                success.incrementAndGet();
            } catch (Throwable ignored) {
            }
        });

        assertThat(success.get()).isEqualTo(2);
        var detail = service.getDetail("C-003");
        assertThat(detail.status()).isEqualTo(ManifestStatus.WEIGHT_DISPUTE);
        assertThat(detail.finalWeight()).isNull();
        assertThat(events.findByManifestManifestNoOrderByIdAsc("C-003")).hasSize(5);
    }

    private void createManifest(String manifestNo) {
        service.create(new CreateManifestRequest(manifestNo, "gen-1", "trans-1", "disp-1",
                List.of(new ManifestItemRequest("HW08", 10, new BigDecimal("100")))));
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
