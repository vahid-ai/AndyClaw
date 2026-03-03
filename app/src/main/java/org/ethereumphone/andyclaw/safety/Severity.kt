package org.ethereumphone.andyclaw.safety

enum class Severity {
    LOW, MEDIUM, HIGH, CRITICAL;

    val displayName: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
}
