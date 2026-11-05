package com.ivor.openstream.presentation.downloads

import androidx.annotation.StringRes
import com.ivor.openstream.R
import com.ivor.openstream.data.local.entity.LocalMediaEntity

enum class LocalMediaSortOrder(@StringRes val labelRes: Int) {
    NAME_ASC(R.string.sort_name_asc),
    NAME_DESC(R.string.sort_name_desc),
    DATE_ADDED_DESC(R.string.sort_date_added_desc),
    DATE_ADDED_ASC(R.string.sort_date_added_asc),
    SIZE_DESC(R.string.sort_size_desc),
    SIZE_ASC(R.string.sort_size_asc),
    DURATION_DESC(R.string.sort_duration_desc),
    DURATION_ASC(R.string.sort_duration_asc);

    fun sort(items: List<LocalMediaEntity>): List<LocalMediaEntity> = when (this) {
        NAME_ASC -> items.sortedBy { it.displayName.lowercase() }
        NAME_DESC -> items.sortedByDescending { it.displayName.lowercase() }
        DATE_ADDED_DESC -> items.sortedByDescending { it.dateAdded }
        DATE_ADDED_ASC -> items.sortedBy { it.dateAdded }
        SIZE_DESC -> items.sortedByDescending { it.sizeBytes }
        SIZE_ASC -> items.sortedBy { it.sizeBytes }
        DURATION_DESC -> items.sortedByDescending { it.durationMs }
        DURATION_ASC -> items.sortedBy { it.durationMs }
    }
}
