// IExecSummaryCallback.aidl
// Callback interface for streaming executive summary updates to the launcher.

package org.ethereumphone.andyclaw.ipc;

oneway interface IExecSummaryCallback {
    // Called with each streamed token during exec summary generation.
    void onSummaryToken(String token);

    // Called when exec summary generation completes with the full text.
    void onSummaryComplete(String fullSummary);

    // Called on generation error.
    void onSummaryError(String message);
}
