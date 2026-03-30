package org.ethereumphone.andyclaw.ExecutionEngine

/**
 * Convenience builder for [ParallelExecutionEngine].
 *
 * Usage:
 * ```
 * val engine = EngineBuilder()
 *     .executor { name, params -> myRegistry.executeTool(name, params) }
 *     .addPreflightCheck(rateLimitCheck)
 *     .addPreflightCheck(paramValidationCheck)
 *     .addPreflightCheck(permissionCheck)
 *     .addPreflightCheck(approvalCheck)
 *     .addPostProcessor(sanitizationProcessor)
 *     .addPostProcessor(truncationProcessor)
 *     .callbacks(myCallbacks)
 *     .build()
 *
 * val batchResult = engine.executeBatch(toolCalls)
 * ```
 */
class EngineBuilder {
    private var executor: ToolExecutor? = null
    private val preflightChecks = mutableListOf<PreflightCheck>()
    private val postProcessors = mutableListOf<PostProcessor>()
    private var callbacks: ExecutionCallbacks? = null

    fun executor(executor: ToolExecutor) = apply { this.executor = executor }

    fun addPreflightCheck(check: PreflightCheck) = apply { preflightChecks.add(check) }

    fun addPostProcessor(processor: PostProcessor) = apply { postProcessors.add(processor) }

    fun callbacks(callbacks: ExecutionCallbacks) = apply { this.callbacks = callbacks }

    fun build(): ParallelExecutionEngine {
        return ParallelExecutionEngine(
            executor = executor ?: error("ToolExecutor is required"),
            preflightChecks = preflightChecks.toList(),
            postProcessors = postProcessors.toList(),
            callbacks = callbacks ?: error("ExecutionCallbacks are required"),
        )
    }
}
