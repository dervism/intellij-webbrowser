package no.dervis.webbrowser.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import no.dervis.webbrowser.WebBrowserController
import no.dervis.webbrowser.domain.AddressBarRequest

/**
 * Editor right-click action: routes the URL currently under the caret (or the
 * editor's selection) to the Web Browser Panel instead of the OS browser.
 *
 * The URL is run through [AddressBarRequest] so a bare hostname / IP /
 * `localhost:3000` is normalised and a non-URL selection becomes a search.
 * Disabled when nothing useful can be extracted from the editor.
 */
class OpenInWebBrowserPanelAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val text = extractTarget(e)
        e.presentation.isEnabledAndVisible = e.project != null && !text.isNullOrBlank()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val raw = extractTarget(e) ?: return
        AddressBarRequest.parse(raw).onSome { request ->
            WebBrowserController.getInstance(project).open(request.target)
        }
    }

    /**
     * Try, in order: explicit selection, then the word under the caret. Both
     * fall back through Kotlin's null-flow if the editor isn't available.
     */
    private fun extractTarget(e: AnActionEvent): String? {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return null
        val selection = editor.selectionModel.selectedText
        if (!selection.isNullOrBlank()) return selection.trim()
        return wordUnderCaret(editor)
    }

    private fun wordUnderCaret(editor: Editor): String? {
        val text = editor.document.charsSequence
        val offset = editor.caretModel.offset.coerceIn(0, text.length)
        if (text.isEmpty()) return null

        var start = offset
        while (start > 0 && isUrlChar(text[start - 1])) start--
        var end = offset
        while (end < text.length && isUrlChar(text[end])) end++
        if (start == end) return null
        return text.subSequence(start, end).toString()
    }

    /**
     * The character class we use to grow the URL under the caret. Permissive
     * enough to keep `localhost:3000/path?x=1` in one piece but stops at
     * whitespace and quotes — the usual editor neighbours.
     */
    private fun isUrlChar(c: Char): Boolean =
        !c.isWhitespace() && c !in URL_TERMINATORS

    private companion object {
        // Characters that can never appear in the middle of a URL we'd want to open.
        private const val URL_TERMINATORS = "\"'<>(){}[]`"
    }
}
