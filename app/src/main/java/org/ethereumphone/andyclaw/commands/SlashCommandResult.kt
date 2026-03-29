package org.ethereumphone.andyclaw.commands

/**
 * Outcome of executing a slash command, surfaced to the user as a system
 * message in the chat bubble list.
 */
sealed interface SlashCommandResult {
    /** Human-readable summary shown in the chat, e.g. "YOLO mode → ON". */
    val message: String

    /** A toggle was flipped. */
    data class Toggled(
        override val message: String,
        val settingId: String,
        val newValue: Boolean,
    ) : SlashCommandResult

    /** A cycle command needs the UI to present options. */
    data class CycleOptions(
        override val message: String,
        val commandId: String,
        val options: List<String>,
        val currentIndex: Int,
    ) : SlashCommandResult

    /** A cycle value was selected. */
    data class CycleSelected(
        override val message: String,
        val commandId: String,
        val selectedOption: String,
    ) : SlashCommandResult

    /** A one-shot action completed. */
    data class ActionDone(
        override val message: String,
    ) : SlashCommandResult

    /** Navigation request — the UI layer should navigate to this route. */
    data class Navigate(
        override val message: String,
        val route: String,
    ) : SlashCommandResult

    /** Shows the list of all available commands. */
    data class HelpList(
        override val message: String,
        val commands: List<SlashCommand>,
    ) : SlashCommandResult

    /** Something went wrong. */
    data class Error(
        override val message: String,
    ) : SlashCommandResult
}
