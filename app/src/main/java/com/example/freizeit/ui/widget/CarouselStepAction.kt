package com.example.freizeit.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/** The carousel's ‹ / › arrows (#67): moves this widget's stored index by [DELTA] (wrapping)
 *  and re-renders it — no deck recompute, so the three cards stay put while cycling. */
class CarouselStepAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[DELTA] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            SuggestionWidgetContent.storeStep(prefs, delta)
        }
        SuggestionWidget().update(context, glanceId)
    }

    companion object {
        val DELTA = ActionParameters.Key<Int>("carousel_delta")
    }
}
