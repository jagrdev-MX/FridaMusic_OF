package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagr.fridamusic.R
import com.jagr.fridamusic.presentation.components.FridaLoadingIndicator
import com.jagr.fridamusic.viewmodels.MoodAndGenresViewModel
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.pages.MoodAndGenres

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodAndGenresScreen(
    onItemClick: (BrowseEndpoint, String) -> Unit,
    onBack: () -> Unit,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
    val sections by viewModel.moodAndGenres.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.mood_and_genres), maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            },
        )

        when {
            sections == null -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) { FridaLoadingIndicator() }

            sections.orEmpty().all { it.items.isEmpty() } -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.collection_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 156.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                sections.orEmpty().forEachIndexed { sectionIndex, section ->
                    if (section.title.isNotBlank()) {
                        item(
                            key = "mood_group_${sectionIndex}_${section.title}",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                            )
                        }
                    }
                    items(
                        items = section.items,
                        key = { item ->
                            "mood_${sectionIndex}_${item.endpoint.browseId}_${item.title}"
                        },
                    ) { item ->
                        MoodAndGenreCard(
                            item = item,
                            onClick = { onItemClick(item.endpoint, item.title) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodAndGenreCard(
    item: MoodAndGenres.Item,
    onClick: () -> Unit,
) {
    val accent = Color(item.stripeColor)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.72f),
                        accent.copy(alpha = 0.96f),
                    ),
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
