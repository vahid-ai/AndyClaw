package org.ethereumphone.andyclaw

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.ethereumphone.andyclaw.ui.chat.ChatInputBar
import org.junit.Rule
import org.junit.Test

class ChatInputBarTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sendButtonIsAlwaysVisible() {
        composeTestRule.setContent {
            MaterialTheme {
                ChatInputBar(
                    isStreaming = false,
                    onSend = {},
                    onCancel = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Send").assertIsDisplayed()
    }

    @Test
    fun sendButtonChangesToCancelWhileStreaming() {
        composeTestRule.setContent {
            MaterialTheme {
                ChatInputBar(
                    isStreaming = true,
                    onSend = {},
                    onCancel = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Send").assertDoesNotExist()
    }

    @Test
    fun sendButtonReturnsAfterStreamingEnds() {
        var streaming by mutableStateOf(true)

        composeTestRule.setContent {
            MaterialTheme {
                ChatInputBar(
                    isStreaming = streaming,
                    onSend = {},
                    onCancel = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Cancel").assertIsDisplayed()

        streaming = false
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Send").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Cancel").assertDoesNotExist()
    }

    @Test
    fun placeholderShowsThinkingWhileStreaming() {
        composeTestRule.setContent {
            MaterialTheme {
                ChatInputBar(
                    isStreaming = true,
                    onSend = {},
                    onCancel = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Thinking...").assertIsDisplayed()
    }

    @Test
    fun placeholderShowsTypeAMessageWhenIdle() {
        composeTestRule.setContent {
            MaterialTheme {
                ChatInputBar(
                    isStreaming = false,
                    onSend = {},
                    onCancel = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Type a message...").assertIsDisplayed()
    }

    @Test
    fun cancelButtonCallsOnCancel() {
        var cancelled = false

        composeTestRule.setContent {
            MaterialTheme {
                ChatInputBar(
                    isStreaming = true,
                    onSend = {},
                    onCancel = { cancelled = true },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Cancel").performClick()

        assert(cancelled) { "Expected onCancel to be called" }
    }
}
