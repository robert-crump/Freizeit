package com.example.freizeit.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/** Wires the widget's refresh icon (issue #54) to [SuggestionWidgetUpdater] — a plain
 *  [ActionCallback] rather than logic inlined at the click site, so it stays a single reusable
 *  hook Glance itself invokes (instantiated by class name, needs no manifest registration). */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SuggestionWidgetUpdater.refreshAndPush(context)
    }
}
