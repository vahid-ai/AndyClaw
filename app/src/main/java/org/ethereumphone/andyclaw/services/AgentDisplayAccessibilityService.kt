package org.ethereumphone.andyclaw.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.IBinder
import android.os.IAgentAccessibilityProxy
import android.os.IAgentDisplayService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-hosted accessibility service that provides UI tree queries for the
 * framework's AgentDisplayService via a binder callback proxy.
 *
 * The framework cannot host this service itself because AccessibilityManagerService
 * cannot resolve/bind services declared in the "android" framework package.
 */
class AgentDisplayAccessibilityService : AccessibilityService() {

    private val proxy = object : IAgentAccessibilityProxy.Stub() {
        override fun getTreeForDisplay(displayId: Int): String =
            buildTreeForDisplay(displayId)

        override fun clickNodeByViewId(displayId: Int, viewId: String): Boolean =
            doClickNode(displayId, viewId)

        override fun setNodeTextByViewId(displayId: Int, viewId: String, text: String): Boolean =
            doSetNodeText(displayId, viewId, text)

        override fun longClickNodeByViewId(displayId: Int, viewId: String): Boolean =
            doLongClickNode(displayId, viewId)

        override fun scrollNodeForwardByViewId(displayId: Int, viewId: String): Boolean =
            doScrollNode(displayId, viewId, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)

        override fun scrollNodeBackwardByViewId(displayId: Int, viewId: String): Boolean =
            doScrollNode(displayId, viewId, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)

        override fun focusNodeByViewId(displayId: Int, viewId: String): Boolean =
            doFocusNode(displayId, viewId)

        override fun getNodeInfoByViewId(displayId: Int, viewId: String): String =
            doGetNodeInfo(displayId, viewId)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "onServiceConnected")

        val info = serviceInfo ?: run {
            Log.e(TAG, "serviceInfo is null")
            return
        }
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        serviceInfo = info
        Log.i(TAG, "flags=0x${Integer.toHexString(info.flags)}")

        registerProxyWithFramework()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed — we query on demand
    }

    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
    }

    // ---- proxy registration ----

    private fun registerProxyWithFramework() {
        try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getService = smClass.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "agentdisplay") as? IBinder
            if (binder == null) {
                Log.e(TAG, "agentdisplay service binder is null")
                return
            }
            val service = IAgentDisplayService.Stub.asInterface(binder)
            service.registerAccessibilityProxy(proxy)
            Log.i(TAG, "Proxy registered with AgentDisplayService")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register proxy with AgentDisplayService", e)
        }
    }

    // ---- tree building (ported from framework AgentDisplayAccessibilityService.java) ----

    private fun buildTreeForDisplay(displayId: Int): String {
        Log.i(TAG, "buildTreeForDisplay: displayId=$displayId")
        return try {
            val allWindows = windowsOnAllDisplays

            // Diagnostics: log known displays
            val displayInfo = buildString {
                for (i in 0 until allWindows.size()) {
                    if (i > 0) append(", ")
                    val id = allWindows.keyAt(i)
                    val wins = allWindows.valueAt(i)
                    append("$id(${wins?.size ?: 0} windows)")
                }
            }
            Log.d(TAG, "buildTreeForDisplay: target=$displayId, displays=[$displayInfo]")

            var windows: List<AccessibilityWindowInfo>? = allWindows.get(displayId)
            if (windows.isNullOrEmpty()) {
                Log.w(TAG, "No windows on display $displayId, fallback to getWindows()")
                windows = getWindows()
                if (windows.isNullOrEmpty()) {
                    Log.w(TAG, "getWindows() also empty")
                    return "{\"windows\":[]}"
                }
                Log.d(TAG, "getWindows() returned ${windows.size} windows (default display fallback)")
            }

            // Collect interactive elements across all windows for the flat summary
            val interactiveElements = mutableListOf<JSONObject>()
            var elementIndex = 0

            val windowsArray = JSONArray()
            for (window in windows) {
                val windowObj = JSONObject().apply {
                    put("id", window.id)
                    put("type", windowTypeToString(window.type))
                    put("title", window.title?.toString() ?: "")
                    put("displayId", window.displayId)
                    val bounds = Rect()
                    window.getBoundsInScreen(bounds)
                    put("bounds", bounds.flattenToString())
                }
                val root = window.getRoot()
                if (root != null) {
                    nodeToJson(root, 0)?.let { windowObj.put("tree", it) }
                    // Collect interactive elements from this window
                    collectInteractiveElements(root, interactiveElements, elementIndex)
                    elementIndex = interactiveElements.size
                    root.recycle()
                } else {
                    Log.d(TAG, "window ${window.id} (${windowTypeToString(window.type)}) has null root")
                }
                windowsArray.put(windowObj)
            }

            val result = JSONObject().apply {
                put("windows", windowsArray)
                // Flat indexed list of all interactive/meaningful elements for quick LLM reference
                if (interactiveElements.isNotEmpty()) {
                    val elemArr = JSONArray()
                    interactiveElements.forEach { elemArr.put(it) }
                    put("elements", elemArr)
                }
            }
            Log.d(TAG, "buildTreeForDisplay: ${windowsArray.length()} windows, ${interactiveElements.size} interactive elements")
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error building tree", e)
            "{\"error\":\"${e.message}\"}"
        }
    }

    /**
     * Walks the tree and collects all interactive or text-bearing nodes into a
     * flat indexed list. This gives the LLM a quick "table of contents" of
     * everything it can act on, without parsing the nested tree.
     */
    private fun collectInteractiveElements(
        node: AccessibilityNodeInfo,
        out: MutableList<JSONObject>,
        startIndex: Int,
        depth: Int = 0,
    ) {
        if (depth > 30 || !node.isVisibleToUser) return

        val isInteractive = node.isClickable || node.isLongClickable ||
            node.isEditable || node.isScrollable || node.isCheckable
        val hasContent = !node.text.isNullOrEmpty() || !node.contentDescription.isNullOrEmpty()

        if (isInteractive || (hasContent && node.viewIdResourceName != null)) {
            val idx = startIndex + out.size
            val elem = JSONObject().apply {
                put("idx", idx)
                put("cls", shortClassName(node.className))
                node.viewIdResourceName?.let { put("id", it) }
                node.text?.let { put("text", it.toString()) }
                node.contentDescription?.let { put("desc", it.toString()) }
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                put("bounds", bounds.flattenToString())
                val flags = mutableListOf<String>()
                if (node.isClickable) flags.add("clickable")
                if (node.isEditable) flags.add("editable")
                if (node.isScrollable) flags.add("scrollable")
                if (node.isCheckable) {
                    flags.add(if (node.isChecked) "checked" else "unchecked")
                }
                if (node.isFocused) flags.add("focused")
                if (flags.isNotEmpty()) put("flags", flags.joinToString(","))
            }
            out.add(elem)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectInteractiveElements(child, out, startIndex, depth + 1)
            child.recycle()
        }
    }

    private fun doClickNode(displayId: Int, viewId: String): Boolean {
        val node = findNodeByViewId(displayId, viewId) ?: return false
        val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.recycle()
        Log.d(TAG, "clickNode $viewId -> $result")
        return result
    }

    private fun doSetNodeText(displayId: Int, viewId: String, text: String): Boolean {
        val node = findNodeByViewId(displayId, viewId) ?: return false
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        node.recycle()
        Log.d(TAG, "setNodeText $viewId -> $result")
        return result
    }

    private fun doLongClickNode(displayId: Int, viewId: String): Boolean {
        val node = findNodeByViewId(displayId, viewId) ?: return false
        val result = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        node.recycle()
        Log.d(TAG, "longClickNode $viewId -> $result")
        return result
    }

    private fun doScrollNode(displayId: Int, viewId: String, action: Int): Boolean {
        val node = findNodeByViewId(displayId, viewId) ?: return false
        val result = node.performAction(action)
        node.recycle()
        Log.d(TAG, "scrollNode $viewId action=$action -> $result")
        return result
    }

    private fun doFocusNode(displayId: Int, viewId: String): Boolean {
        val node = findNodeByViewId(displayId, viewId) ?: return false
        val result = node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        node.recycle()
        Log.d(TAG, "focusNode $viewId -> $result")
        return result
    }

    private fun doGetNodeInfo(displayId: Int, viewId: String): String {
        val node = findNodeByViewId(displayId, viewId)
            ?: return "{\"error\":\"Node not found: $viewId\"}"
        return try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            JSONObject().apply {
                put("viewId", node.viewIdResourceName ?: viewId)
                put("className", node.className?.toString() ?: "")
                node.text?.let { put("text", it.toString()) }
                node.contentDescription?.let { put("contentDescription", it.toString()) }
                put("bounds", JSONObject().apply {
                    put("left", bounds.left)
                    put("top", bounds.top)
                    put("right", bounds.right)
                    put("bottom", bounds.bottom)
                })
                put("enabled", node.isEnabled)
                put("clickable", node.isClickable)
                put("scrollable", node.isScrollable)
                put("focused", node.isFocused)
                put("checked", node.isChecked)
                put("selected", node.isSelected)
                put("editable", node.isEditable)
                put("childCount", node.childCount)
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "getNodeInfo $viewId failed", e)
            "{\"error\":\"${e.message}\"}"
        } finally {
            node.recycle()
        }
    }

    private fun findNodeByViewId(displayId: Int, viewId: String): AccessibilityNodeInfo? {
        val allWindows = windowsOnAllDisplays
        var windows: List<AccessibilityWindowInfo>? = allWindows.get(displayId)
        if (windows.isNullOrEmpty()) windows = getWindows()
        if (windows == null) return null

        for (window in windows) {
            val root = window.getRoot() ?: continue
            val found = root.findAccessibilityNodeInfosByViewId(viewId)
            root.recycle()
            if (!found.isNullOrEmpty()) {
                // Recycle extras, return the first match
                for (i in 1 until found.size) found[i].recycle()
                return found[0]
            }
        }
        return null
    }

    // ---- JSON serialisation (compact, LLM-optimised) ----

    /**
     * Checks whether a node is "meaningful" — i.e. it carries information or
     * interaction that the LLM needs to know about. Nodes that are just layout
     * wrappers (no text, no id, not interactive, not scrollable) are candidates
     * for collapsing to reduce tree depth and token count.
     */
    private fun isMeaningful(node: AccessibilityNodeInfo): Boolean {
        if (node.viewIdResourceName != null) return true
        if (!node.text.isNullOrEmpty()) return true
        if (!node.contentDescription.isNullOrEmpty()) return true
        if (node.isClickable || node.isLongClickable) return true
        if (node.isEditable) return true
        if (node.isScrollable) return true
        if (node.isCheckable) return true
        if (node.isFocusable) return true
        return false
    }

    /** Strip package prefix: "android.widget.TextView" → "TextView" */
    private fun shortClassName(className: CharSequence?): String {
        val full = className?.toString() ?: return ""
        val dot = full.lastIndexOf('.')
        return if (dot >= 0) full.substring(dot + 1) else full
    }

    private fun nodeToJson(node: AccessibilityNodeInfo, depth: Int): JSONObject? {
        if (depth > 30) return null
        // Skip invisible nodes entirely — they add noise without value
        if (!node.isVisibleToUser) return null
        return try {
            // Collapse: if this node is not meaningful and has exactly one
            // visible child, skip this node and return the child directly.
            if (!isMeaningful(node) && node.childCount > 0) {
                var soleVisibleChild: AccessibilityNodeInfo? = null
                var visibleCount = 0
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    if (child.isVisibleToUser) {
                        visibleCount++
                        if (visibleCount == 1) {
                            soleVisibleChild = child
                        } else {
                            child.recycle()
                        }
                    } else {
                        child.recycle()
                    }
                    if (visibleCount > 1) break
                }
                if (visibleCount == 1 && soleVisibleChild != null) {
                    val result = nodeToJson(soleVisibleChild, depth) // same depth — we're collapsing
                    soleVisibleChild.recycle()
                    return result
                }
                // Not collapsible (0 or 2+ visible children) — recycle probe ref and fall through
                soleVisibleChild?.recycle()
            }

            JSONObject().apply {
                put("cls", shortClassName(node.className))
                node.viewIdResourceName?.let { put("id", it) }
                node.text?.let { put("text", it.toString()) }
                node.contentDescription?.let { put("desc", it.toString()) }

                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                put("bounds", bounds.flattenToString())

                // Only include boolean flags when true — saves ~60% on flag tokens
                if (node.isClickable) put("clickable", true)
                if (node.isEnabled) put("enabled", true)
                if (node.isEditable) put("editable", true)
                if (node.isCheckable) {
                    put("checkable", true)
                    put("checked", node.isChecked)
                }
                if (node.isScrollable) put("scrollable", true)
                if (node.isFocused) put("focused", true)

                val childCount = node.childCount
                if (childCount > 0) {
                    val children = JSONArray()
                    for (i in 0 until childCount) {
                        val child = node.getChild(i) ?: continue
                        nodeToJson(child, depth + 1)?.let { children.put(it) }
                        child.recycle()
                    }
                    if (children.length() > 0) put("children", children)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun windowTypeToString(type: Int): String = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "application"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "input_method"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "system"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "overlay"
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "divider"
        else -> "unknown"
    }

    companion object {
        private const val TAG = "AgentDisplayA11y"
    }
}
