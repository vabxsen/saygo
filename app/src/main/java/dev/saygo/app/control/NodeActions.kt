package dev.saygo.app.control

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import dev.saygo.app.commands.Command
import dev.saygo.app.commands.TextOperation
import dev.saygo.app.data.SessionState

/** Resolves one explicit action locally. No coordinates, fuzzy matching or retained screen data. */
internal object NodeActions {
    private fun normalized(value: CharSequence?) = value?.toString()?.trim()
        ?.replace(Regex("\\s+"), " ")?.lowercase(java.util.Locale.ROOT)

    fun execute(root: AccessibilityNodeInfo, command: Command) {
        try {
            when (command) {
                is Command.Tap -> tap(root, command)
                is Command.EditText -> edit(root, command)
                else -> error("Not a node command")
            }
        } catch (_: SecurityException) {
            fail("Android blocked access to this control.")
        } catch (_: IllegalStateException) {
            fail("The screen changed. Try the command again.")
        }
    }

    private fun tap(root: AccessibilityNodeInfo, command: Command.Tap) {
        val nodes = mutableListOf(root)
        val parents = mutableListOf(-1)
        try {
            var index = 0
            // Complete the bounded scan before acting, so a partial scan cannot hide ambiguity.
            while (index < nodes.size) {
                val node = nodes[index]
                for (childIndex in 0 until node.childCount) {
                    if (nodes.size >= 600) { fail("This screen has too many controls. Open a simpler screen and try again."); return }
                    node.getChild(childIndex)?.let { nodes.add(it); parents.add(index) }
                }
                index++
            }
            val action = if (command.longPress) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
            val label = normalized(command.label)
            val targets = mutableListOf<AccessibilityNodeInfo>()
            var matchedUnusable = false
            nodes.forEachIndexed { i, node ->
                if (!node.isVisibleToUser || node.isPassword) return@forEachIndexed
                val labels = listOf(node.contentDescription, node.hintText, if (node.isEditable) null else node.text)
                if (labels.none { !it.isNullOrBlank() && normalized(it) == label }) return@forEachIndexed
                var candidate = i
                while (candidate >= 0 && nodes[candidate].actionList.none { it.id == action }) candidate = parents[candidate]
                val target = nodes.getOrNull(candidate)
                if (target == null || !node.isEnabled || !target.isEnabled || !target.isVisibleToUser || target.windowId != root.windowId) {
                    matchedUnusable = true
                } else if (targets.none { it == target }) targets.add(target)
            }
            when {
                targets.size > 1 || (targets.isNotEmpty() && matchedUnusable) -> fail("More than one control matches. Use a more specific label or choose it manually.")
                targets.isEmpty() -> fail("No unique usable control has that label. Say the exact button label on screen.")
                else -> {
                    val target = targets.single()
                    if (!target.refresh() || !target.isVisibleToUser || !target.isEnabled) {
                        fail("The control changed. Try again.")
                        return
                    }
                    val ok = target.performAction(action)
                    SessionState.report(if (ok) (if (command.longPress) "Long press requested." else "Tap requested.") else "This app couldn’t perform that action.", ok)
                }
            }
        } finally {
            @Suppress("DEPRECATION")
            nodes.drop(1).forEach { it.recycle() }
        }
    }

    private fun edit(root: AccessibilityNodeInfo, command: Command.EditText) {
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (field == null) { fail("Tap a text field first, then dictate your command."); return }
        try {
            if (!field.refresh() || !field.isFocused || !field.isEditable || !field.isEnabled || !field.isVisibleToUser || field.windowId != root.windowId) {
                fail("Tap a usable text field first."); return
            }
            if (field.isPassword) { fail("Use your keyboard for password fields."); return }
            val existing = field.text?.toString().orEmpty().let { if (field.isShowingHintText) "" else it }
            val action: Int
            val args = Bundle()
            if (command.operation == TextOperation.SELECT_ALL) {
                action = AccessibilityNodeInfo.ACTION_SET_SELECTION
                args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, existing.length)
            } else {
                action = AccessibilityNodeInfo.ACTION_SET_TEXT
                val value = when (command.operation) {
                    TextOperation.CLEAR -> ""
                    TextOperation.REPLACE -> command.text
                    TextOperation.INSERT -> {
                        val start = field.textSelectionStart.takeIf { it in 0..existing.length } ?: existing.length
                        val end = field.textSelectionEnd.takeIf { it in 0..existing.length } ?: start
                        existing.replaceRange(minOf(start, end), maxOf(start, end), command.text)
                    }
                    TextOperation.SELECT_ALL -> error("Handled above")
                }
                if (value.length > 20000) { fail("This text is too long for voice editing."); return }
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
            if (field.actionList.none { it.id == action }) { fail("This app doesn’t allow that text action."); return }
            val ok = field.performAction(action, args)
            // Never speak or retain the dictated text or the field contents in feedback.
            SessionState.report(if (ok) when (command.operation) {
                TextOperation.SELECT_ALL -> "Text selected."
                TextOperation.CLEAR -> "Text cleared."
                else -> "Text entered."
            } else "This app couldn’t edit that field.", ok)
        } finally {
            @Suppress("DEPRECATION")
            field.recycle()
        }
    }

    private fun fail(message: String) = SessionState.report(message, false)
}
