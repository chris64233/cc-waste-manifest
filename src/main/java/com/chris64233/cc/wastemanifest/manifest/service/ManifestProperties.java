package com.chris64233.cc.wastemanifest.manifest.service;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "waste-manifest")
public class ManifestProperties {

    /** 处置方称重与申报总重允许的差异比例，默认 5%。 */
    private BigDecimal weightToleranceRatio = new BigDecimal("0.05");

    public BigDecimal getWeightToleranceRatio() {
        return weightToleranceRatio;
    }

    public void setWeightToleranceRatio(BigDecimal weightToleranceRatio) {
        this.weightToleranceRatio = weightToleranceRatio;
    }
}
