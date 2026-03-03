package org.ethereumphone.andyclaw.safety

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class ValidationErrorCode {
    EMPTY, TOO_LONG, TOO_SHORT, FORBIDDEN_CONTENT, INVALID_ENCODING, SUSPICIOUS_PATTERN,
}

data class ValidationError(
    val code: ValidationErrorCode,
    val message: String,
)

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError>,
    val warnings: List<String>,
) {
    companion object {
        fun ok() = ValidationResult(isValid = true, errors = emptyList(), warnings = emptyList())
    }
}

class Validator(
    private val maxLength: Int = DEFAULT_MAX_LENGTH,
    private val minLength: Int = DEFAULT_MIN_LENGTH,
    private val forbiddenPatterns: Set<String> = emptySet(),
) {

    fun validate(input: String): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<String>()

        if (input.isEmpty()) {
            errors.add(ValidationError(ValidationErrorCode.EMPTY, "Input is empty"))
            return ValidationResult(false, errors, warnings)
        }

        if (input.length > maxLength) {
            errors.add(
                ValidationError(
                    ValidationErrorCode.TOO_LONG,
                    "Input exceeds maximum length ($maxLength characters)"
                )
            )
        }

        if (input.length < minLength) {
            errors.add(
                ValidationError(
                    ValidationErrorCode.TOO_SHORT,
                    "Input is shorter than minimum length ($minLength characters)"
                )
            )
        }

        if ('\u0000' in input) {
            errors.add(
                ValidationError(
                    ValidationErrorCode.INVALID_ENCODING,
                    "Input contains null bytes"
                )
            )
        }

        val lower = input.lowercase()
        for (forbidden in forbiddenPatterns) {
            if (forbidden.lowercase() in lower) {
                errors.add(
                    ValidationError(
                        ValidationErrorCode.FORBIDDEN_CONTENT,
                        "Input contains forbidden pattern: $forbidden"
                    )
                )
            }
        }

        if (input.length > 100) {
            val whitespaceRatio = input.count { it.isWhitespace() }.toFloat() / input.length
            if (whitespaceRatio > 0.9f) {
                warnings.add("Suspiciously high whitespace ratio (${(whitespaceRatio * 100).toInt()}%) — possible padding attack")
            }
        }

        if (input.length >= 50) {
            var maxRepeat = 1
            var currentRepeat = 1
            for (i in 1 until input.length) {
                if (input[i] == input[i - 1]) {
                    currentRepeat++
                    if (currentRepeat > maxRepeat) maxRepeat = currentRepeat
                } else {
                    currentRepeat = 1
                }
            }
            if (maxRepeat > 20) {
                warnings.add("Excessive character repetition detected ($maxRepeat consecutive identical characters)")
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
        )
    }

    fun validateToolParams(params: kotlinx.serialization.json.JsonElement): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<String>()
        validateJsonRecursive(params, errors, warnings)
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
        )
    }

    private fun validateJsonRecursive(
        element: kotlinx.serialization.json.JsonElement,
        errors: MutableList<ValidationError>,
        warnings: MutableList<String>,
    ) {
        when (element) {
            is JsonPrimitive -> {
                if (element.isString) {
                    val result = validate(element.content)
                    errors.addAll(result.errors)
                    warnings.addAll(result.warnings)
                }
            }
            is JsonObject -> element.values.forEach { validateJsonRecursive(it, errors, warnings) }
            is JsonArray -> element.forEach { validateJsonRecursive(it, errors, warnings) }
        }
    }

    companion object {
        const val DEFAULT_MAX_LENGTH = 100_000
        const val DEFAULT_MIN_LENGTH = 1
    }
}
