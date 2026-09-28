package com.chris64233.cc.wastemanifest.manifest;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 联单活动操作守卫：差错更正与运输异常处置互斥。
 * 登记活动操作时插入唯一守卫行（联单号唯一），操作终态时释放。
 */
@Component
public class ActiveOpGuard {

    private final ActiveManifestOpRepository repository;

    public ActiveOpGuard(ActiveManifestOpRepository repository) {
        this.repository = repository;
    }

    public boolean isActive(String manifestNo) {
        return repository.findByManifestNo(manifestNo).isPresent();
    }

    /** 返回当前活动守卫；不存在时返回 null。 */
    public ActiveManifestOp find(String manifestNo) {
        return repository.findByManifestNo(manifestNo).orElse(null);
    }

    /** 占用守卫；已被其他活动操作占用时返回 false，唯一约束冲突兜底也返回 false。 */
    public boolean tryAcquire(String manifestNo, ActiveOpType opType, String refNo, int baseVersionNo) {
        if (repository.findByManifestNo(manifestNo).isPresent()) {
            return false;
        }
        try {
            repository.saveAndFlush(new ActiveManifestOp(manifestNo, opType, refNo, baseVersionNo));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    /** 仅当守卫确实指向该操作时释放，避免误删后来操作的守卫。 */
    public void release(String manifestNo, String refNo) {
        repository.findByManifestNo(manifestNo)
                .filter(op -> op.getRefNo().equals(refNo))
                .ifPresent(repository::delete);
        repository.flush();
    }
}
