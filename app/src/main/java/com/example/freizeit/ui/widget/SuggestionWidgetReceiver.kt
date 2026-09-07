package com.example.freizeit.ui.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** System entry point registered in the manifest (`suggestion_widget_info.xml`) — Glance's
 *  bridge from the classic AppWidget broadcast lifecycle to [SuggestionWidget]'s Compose one. */
class SuggestionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SuggestionWidget()
}
