package com.btcautotrader.engine;

record SignalTuning(
        double rsiBuyThreshold,
        double rsiSellThreshold,
        double rsiOverbought,
        double minAdx,
        double minVolumeRatio,
        double breakoutPct,
        int minConfirmations,
        double maxExtensionPct,
        double minMaLongSlopePct
) {
}
