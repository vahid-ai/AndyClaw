// ILauncherService.aidl
// Service interface for the launcher to communicate with AndyClaw.

package org.ethereumphone.andyclaw.ipc;

import org.ethereumphone.andyclaw.ipc.ILauncherCallback;

interface ILauncherService {
    // Returns true if AndyClaw has been set up (user_story.md exists with content).
    boolean isSetup();

    // Returns the user-chosen AI name, or "AndyClaw" if not configured.
    String getAiName();

    // Sends a text prompt to the agent and streams the response via callback.
    // sessionId groups messages into a conversation for multi-turn context.
    void sendPrompt(String prompt, String sessionId, ILauncherCallback callback);

    // Transcribes an audio file using Whisper large-v3-turbo.
    // audioFd is a file descriptor for the recorded audio file (cross-process safe).
    void transcribeAudio(in ParcelFileDescriptor audioFd, ILauncherCallback callback);

    // Clears conversation history for a given session.
    void clearSession(String sessionId);

    // Sends a prompt from the lockscreen voice flow. After execution, the
    // executive summary is updated to reflect the command that was run.
    void sendLockscreenPrompt(String prompt, String sessionId, ILauncherCallback callback);

    // Returns recent launcher sessions as a JSON array.
    // Each object has: id, title, updatedAt (epoch ms).
    String getRecentSessions(int limit);

    // Returns messages for a session as a JSON array.
    // Each object has: role ("user"/"assistant"/"system"/"tool"), content, timestamp.
    String getSessionMessages(String sessionId);
}
