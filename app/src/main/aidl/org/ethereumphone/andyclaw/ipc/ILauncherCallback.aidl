// ILauncherCallback.aidl
// Callback interface for streaming agent responses back to the launcher.

package org.ethereumphone.andyclaw.ipc;

oneway interface ILauncherCallback {
    // Called for each streamed text token from the LLM.
    void onToken(String text);

    // Called when the agent invokes a tool (for UI status display).
    void onToolExecution(String toolName);

    // Called when the full response is complete.
    void onComplete(String fullText);

    // Called on error.
    void onError(String message);

    // Called when audio transcription is complete.
    void onTranscription(String text);
}
