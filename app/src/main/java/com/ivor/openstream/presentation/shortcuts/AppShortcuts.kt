package com.ivor.openstream.presentation.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.ivor.openstream.MainActivity
import com.ivor.openstream.R

/** Launcher long-press targets. Published at runtime so they work for every applicationId. */
enum class AppShortcut(val id: String, val label: String, val icon: Int) {
    CONTINUE_WATCHING("continue_watching", "Continue watching", R.drawable.ic_shortcut_continue),
    SEARCH("search", "Search", R.drawable.ic_shortcut_search),
    DOWNLOADS("downloads", "Downloads", R.drawable.ic_shortcut_downloads);

    companion object {
        const val EXTRA = "com.ivor.openstream.SHORTCUT"

        fun from(intent: Intent?): AppShortcut? =
            intent?.getStringExtra(EXTRA)?.let { id -> entries.firstOrNull { it.id == id } }

        fun publish(context: Context) {
            val shortcuts = entries.mapIndexed { rank, shortcut ->
                ShortcutInfoCompat.Builder(context, shortcut.id)
                    .setShortLabel(shortcut.label)
                    .setIcon(IconCompat.createWithResource(context, shortcut.icon))
                    .setRank(rank)
                    .setIntent(
                        Intent(context, MainActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .putExtra(EXTRA, shortcut.id)
                    )
                    .build()
            }
            runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
        }
    }
}

/** A shortcut launch waiting to be handled; [nonce] makes a repeat launch count as new. */
data class ShortcutRequest(val shortcut: AppShortcut, val nonce: Long = System.nanoTime())
