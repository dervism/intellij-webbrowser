package no.dervis.webbrowser

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * Balloons for when the plugin refuses a URL, so a refused setting or page is
 * reported instead of silently doing nothing. The group is registered in plugin.xml.
 */
object WebBrowserNotifications {

    private const val GROUP = "Web Browser Panel"

    fun warn(project: Project, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP)
            .createNotification(title, content, NotificationType.WARNING)
            .notify(project)
    }
}
