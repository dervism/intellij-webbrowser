package no.dervis.webbrowser.ui

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import org.cef.browser.CefFrame

/**
 * Synchronizes the browser's clipboard with the system clipboard.
 * Handles copy/paste events between the JCEF browser and the IDE/OS.
 *
 * JCEF browsers run in a separate process with an isolated clipboard.
 * This synchronizer bridges that gap by:
 * - Injecting JavaScript to capture copy events and sync to system clipboard
 * - Injecting JavaScript to sync paste events from system clipboard
 * - Creating a bidirectional bridge between browser and IDE clipboard
 */
internal object ClipboardSynchronizer {
    private val systemClipboard = Toolkit.getDefaultToolkit().systemClipboard

    /**
     * JavaScript code injected into every page to enable clipboard sync.
     * Intercepts copy/paste events and bridges them to the system clipboard.
     */
    private val CLIPBOARD_SYNC_SCRIPT = """
        (function() {
          if (window.__clipboardSyncInstalled) return;

          // Intercept copy events: send copied text to system clipboard
          document.addEventListener('copy', function(e) {
            const selected = window.getSelection().toString();
            if (selected) {
              // The actual sync to system clipboard happens via DevTools console
              // We store the text in a global variable that can be read
              window.__lastCopiedText = selected;
              // Log it to console - JCEF will capture this and we can sync to system
              console.log('__CLIPBOARD_COPY__:' + selected);
            }
          }, true);

          // Intercept cut events the same way
          document.addEventListener('cut', function(e) {
            const selected = window.getSelection().toString();
            if (selected) {
              window.__lastCopiedText = selected;
              console.log('__CLIPBOARD_COPY__:' + selected);
            }
          }, true);

          // Store last system clipboard content to provide in paste events
          window.__lastSystemClipboard = '';

          // Try to use modern Clipboard API if available
          if (navigator.clipboard) {
            const originalWrite = navigator.clipboard.writeText;
            navigator.clipboard.writeText = async function(text) {
              window.__lastCopiedText = text;
              console.log('__CLIPBOARD_COPY__:' + text);
              try {
                return await originalWrite.call(navigator.clipboard, text);
              } catch (e) {
                return Promise.resolve();
              }
            };

            const originalRead = navigator.clipboard.readText;
            navigator.clipboard.readText = async function() {
              if (window.__lastSystemClipboard) {
                return Promise.resolve(window.__lastSystemClipboard);
              }
              try {
                return await originalRead.call(navigator.clipboard);
              } catch (e) {
                return Promise.resolve(window.__lastSystemClipboard || '');
              }
            };
          }

          window.__clipboardSyncInstalled = true;
        })();
    """.trimIndent()

    /**
     * Inject clipboard synchronization script into a page frame.
     * Should be called in the onLoadEnd handler.
     */
    fun injectSyncScript(cefFrame: CefFrame, url: String) {
        cefFrame.executeJavaScript(CLIPBOARD_SYNC_SCRIPT, url, 0)
    }

    /**
     * Get the system clipboard content as text.
     * Returns null if clipboard doesn't contain text.
     */
    fun getSystemClipboardText(): String? {
        return try {
            val data = systemClipboard.getContents(null)
            if (data != null && data.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                data.getTransferData(DataFlavor.stringFlavor) as? String
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Set the system clipboard content to the given text.
     */
    fun setSystemClipboardText(text: String) {
        try {
            systemClipboard.setContents(StringSelection(text), null)
        } catch (e: Exception) {
            // Silently fail if clipboard access fails
        }
    }
}
