package com.example.tigerplayer.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.* // Using Rounded icons for the Pixel/Expressive feel

/**
 * A centralized dictionary for all app icons.
 * Shifted to Rounded icons for a modern Google Pixel / Material Expressive feel.
 */
object WitcherIcons {
    // --- Navigation ---
    val Home = Icons.Rounded.Home
    val Library = Icons.Rounded.LibraryMusic
    val Cloud = Icons.Rounded.CloudSync
    val Settings = Icons.Rounded.Settings

    // --- Playback Controls ---
    val Play = Icons.Rounded.PlayArrow
    val Pause = Icons.Rounded.Pause
    val Next = Icons.Rounded.SkipNext
    val Previous = Icons.Rounded.SkipPrevious
    val Shuffle = Icons.Rounded.Shuffle
    val Repeat = Icons.Rounded.Repeat
    val RepeatOne = Icons.Rounded.RepeatOne

    // --- Library & Media Details ---
    val LosslessBadge = Icons.Rounded.GraphicEq  
    val Album = Icons.Rounded.Album
    val Artist = Icons.Rounded.Person
    val Playlist = Icons.AutoMirrored.Rounded.QueueMusic
    val Duration = Icons.Rounded.Timer

    // --- Actions & UI Elements ---
    val Favorite = Icons.Rounded.Favorite
    val FavoriteBorder = Icons.Rounded.FavoriteBorder
    val Add = Icons.Rounded.Add
    val Options = Icons.Rounded.MoreVert
    val Expand = Icons.Rounded.KeyboardArrowUp
    val Collapse = Icons.Rounded.KeyboardArrowDown
    val Search = Icons.Rounded.Search

    val Back = Icons.AutoMirrored.Rounded.ArrowBack

    val VolumeUp = Icons.AutoMirrored.Rounded.VolumeUp         
    val Headphones = Icons.Rounded.Headphones
    val Close = Icons.Rounded.Close
    val More = Icons.Rounded.MoreVert
}
