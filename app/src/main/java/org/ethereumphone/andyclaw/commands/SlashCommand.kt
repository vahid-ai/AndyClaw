package org.ethereumphone.andyclaw.commands

/**
 * Defines a single slash command. There are three flavors:
 *
 * - [Toggle] – flips a boolean setting on/off.
 * - [Cycle]  – steps through an ordered list of options.
 * - [Action] – runs a one-shot operation (clear chat, open settings, etc.).
 */
sealed interface SlashCommand {
    /** The slash token, e.g. "yolo", "model". Always lowercase, no leading slash. */
    val id: String
    /** Short human-readable label shown in the command list. */
    val label: String
    /** One-line description shown next to the label. */
    val description: String

    data class Toggle(
        override val id: String,
        override val label: String,
        override val description: String,
    ) : SlashCommand

    data class Cycle(
        override val id: String,
        override val label: String,
        override val description: String,
    ) : SlashCommand

    data class Action(
        override val id: String,
        override val label: String,
        override val description: String,
    ) : SlashCommand
}
