

package com.jagr.fridamusic.models

import com.music.innertube.models.YTItem
import com.jagr.fridamusic.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
