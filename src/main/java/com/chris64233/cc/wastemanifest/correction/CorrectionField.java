package com.chris64233.cc.wastemanifest.correction;

/**
 * 可更正的联单字段。
 */
public enum CorrectionField {
    /** 包装数量：产生方、承运方、处置方均经手点验，三方都需确认 */
    PACKAGE_COUNT,
    /** 申报重量：产生方申报、处置方核重结算，两方确认 */
    WEIGHT,
    /** 废物类别：产生方申报、处置方接收，且必须监管复核 */
    WASTE_CATEGORY
}
