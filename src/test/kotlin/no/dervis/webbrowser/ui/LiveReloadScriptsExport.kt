package no.dervis.webbrowser.ui

import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes the live-reload scripts to the folder given as the only argument, so the
 * browser checks in src/test/js/livereload run exactly what the plugin sends.
 * Used by `./gradlew liveReloadBrowserTest`.
 */
fun main(args: Array<String>) {
    val dir = Files.createDirectories(Path.of(args.single()))
    mapOf(
        "save.js" to LiveReloadScripts.SAVE_SCROLL_AND_RELOAD,
        "restore.js" to LiveReloadScripts.RESTORE_SCROLL,
        "hotswap.js" to LiveReloadScripts.HOT_SWAP_CSS,
    ).forEach { (name, script) -> Files.writeString(dir.resolve(name), script) }
}
