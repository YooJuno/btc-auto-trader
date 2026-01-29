package com.hvs.btctrader.paper;

import jakarta.validation.constraints.DecimalMin;

public class PaperResetRequest {
	@DecimalMin("10000.0")
	private double initialCash;

	public double getInitialCash() {
		return initialCash;
	}

	public void setInitialCash(double initialCash) {
		this.initialCash = initialCash;
	}
}
