package com.tsystemsmms.cmcc.cmccoperator.crds;

public enum ScalingTarget {
    cae,
    headless;

    public boolean isType(String type) {
        return this.toString().equals(type);
    }
}
