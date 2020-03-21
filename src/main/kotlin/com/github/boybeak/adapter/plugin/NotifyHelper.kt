package com.github.boybeak.adapter.plugin

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project

object NotifyHelper {

    private const val GROUP_ID_WARNING = "any-adapter.warning"
    private const val GROUP_ID_INFO = "any-adapter.info"
    private const val GROUP_ID_ERROR = "any-adapter.error"

    fun showError(project: Project, title: String, content: String) {
        show(project, GROUP_ID_ERROR, title, content, NotificationType.ERROR)
    }
    fun showInfo(project: Project, title: String, content: String) {
        show(project, GROUP_ID_INFO, title, content, NotificationType.INFORMATION)
    }
    fun showWarning(project: Project, title: String, content: String) {
        show(project, GROUP_ID_WARNING, title, content, NotificationType.WARNING)
    }
    private fun show(project: Project, groupId: String, title: String, content: String, type: NotificationType) {
        Notifications.Bus.notify(
            Notification(groupId, title,content, type),
            project
        )
    }
}