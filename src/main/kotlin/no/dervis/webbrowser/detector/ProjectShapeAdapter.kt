package no.dervis.webbrowser.detector

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import no.dervis.webbrowser.domain.DevServerDetector

/**
 * IntelliJ-side adapter that produces a [DevServerDetector.ProjectShape]
 * for [project] and runs the pure detector. Kept in its own package so the
 * domain stays free of IntelliJ types.
 *
 * Scans only the project base directory and a few shallow children — we
 * don't need a full recursive walk to spot `vite.config.ts` or `.storybook/`.
 * `package.json` (when present) is parsed with a minimal regex over its
 * `"scripts"` block; we deliberately avoid a JSON dependency for one
 * surface-level lookup.
 */
fun detectDevServers(project: Project): List<DevServerDetector.Suggestion> {
    val basePath = project.basePath ?: return emptyList()
    val baseDir: VirtualFile = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return emptyList()
    val shape = buildShape(basePath, baseDir)
    return DevServerDetector.detect(shape)
}

private fun buildShape(basePath: String, baseDir: VirtualFile): DevServerDetector.ProjectShape {
    val seen = LinkedHashSet<String>()
    // Top-level files & dirs.
    baseDir.children?.forEach { child ->
        seen += child.name
        if (child.isDirectory) {
            // One level deep is enough to see e.g. `.storybook/main.ts` or
            // `src/App.tsx` — anything deeper than that isn't load-bearing
            // for our detection rules.
            child.children?.forEach { grand -> seen += "${child.name}/${grand.name}" }
        }
    }
    val scripts = baseDir.findChild("package.json")?.let(::readPackageJsonScripts).orEmpty()
    return DevServerDetector.ProjectShape(basePath = basePath, files = seen, packageJsonScripts = scripts)
}

private fun readPackageJsonScripts(packageJson: VirtualFile): Map<String, String> {
    return try {
        val text = String(packageJson.contentsToByteArray(), Charsets.UTF_8)
        val scriptsBlock = Regex("""\"scripts\"\s*:\s*\{([^}]*)}""", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.groupValues?.getOrNull(1)
            ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        // Match "key": "value" pairs inside the scripts block. Doesn't handle
        // escaped quotes inside values, which is good enough for the vast
        // majority of package.json files — we only need the script *names*
        // for matching.
        Regex(""""([^"\\]+)"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""").findAll(scriptsBlock).forEach {
            out[it.groupValues[1].lowercase()] = it.groupValues[2]
        }
        out
    } catch (e: Exception) {
        LOG.info("Failed to parse package.json for dev-server detection", e)
        emptyMap()
    }
}

private val LOG = Logger.getInstance("no.dervis.webbrowser.detector.ProjectShapeAdapter")
