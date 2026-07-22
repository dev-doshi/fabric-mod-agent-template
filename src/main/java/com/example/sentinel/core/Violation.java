package com.example.sentinel.core;

/**
 * One detection from a check. {@code weight} is how much violation-level to add (scaled by how far
 * outside the valid envelope the action was); {@code detail} is a human-readable reason for alerts.
 */
public record Violation(String checkId, double weight, String detail) {
	public static final Violation NONE = new Violation("", 0.0, "");

	public boolean isFlag() {
		return weight > 0.0;
	}

	public static Violation of(String checkId, double weight, String detail) {
		return new Violation(checkId, weight, detail);
	}
}
