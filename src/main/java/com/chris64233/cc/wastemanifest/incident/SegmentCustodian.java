package com.chris64233.cc.wastemanifest.incident;

import com.chris64233.cc.wastemanifest.manifest.CustodianRole;

/**
 * 运输段保管方：承运方运输 / 处置方持有。
 */
public enum SegmentCustodian {
    TRANSPORTER,
    DISPOSER;

    public static SegmentCustodian of(CustodianRole role) {
        return switch (role) {
            case TRANSPORTER -> TRANSPORTER;
            case DISPOSER -> DISPOSER;
            case GENERATOR -> throw new IllegalArgumentException("运输段交接不涉及产生方: " + role);
        };
    }
}
