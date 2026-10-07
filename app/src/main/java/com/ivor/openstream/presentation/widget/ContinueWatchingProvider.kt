package com.ivor.openstream.presentation.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home-screen entry point for the Continue Watching widget. Only [onUpdate] needs work: the widget
 * is redrawn by [ContinueWatchingWidgetRefresher] whenever progress changes.
 */
@AndroidEntryPoint
class ContinueWatchingProvider : AppWidgetProvider() {

    @Inject
    lateinit var widget: ContinueWatchingWidget

    /** A plain receiver has no lifecycle, so the work runs on its own scope. */
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        // onReceive has an ANR budget and reading progress is a suspend query, so keep the
        // receiver alive until it finishes.
        val pendingResult = goAsync()
        scope.launch {
            try {
                widget.render()
            } finally {
                pendingResult.finish()
            }
        }
    }
}