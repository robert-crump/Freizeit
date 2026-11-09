package com.example.freizeit.data.repository

import android.content.Context
import com.example.freizeit.data.dao.VisitDao
import com.example.freizeit.data.dao.checkIn
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.geofence.GeofenceNotifications

/**
 * The in-app check-in path (Check-in tab, map sheet, Home card): logs the visit and clears that
 * place's check-in prompt, if one is up (#81). A same-day visit already keeps the place from
 * prompting again, so a prompt left up would only offer a duplicate check-in.
 */
class CheckInRecorder(private val context: Context, private val visitDao: VisitDao) {

    /** Returns the new visit's id, so the caller can offer Undo. */
    suspend fun checkIn(poi: Poi, visitedAt: Long): Long {
        val visitId = visitDao.checkIn(poi, visitedAt = visitedAt)
        GeofenceNotifications.cancelPrompt(context, poi.id)
        return visitId
    }
}
