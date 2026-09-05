package com.jagr.fridamusic.utils

import android.content.res.Resources
import com.jagr.fridamusic.R
import com.jagr.fridamusic.db.entities.Artist
import com.jagr.fridamusic.db.entities.Playlist

fun Playlist.songCountText(resources: Resources): String? = effectiveSongCount?.let {
    resources.getQuantityString(R.plurals.n_song, it, it)
}

fun Artist.songCountText(resources: Resources): String? = effectiveSongCount?.let {
    resources.getString(R.string.library_artist_song_count, it)
}
