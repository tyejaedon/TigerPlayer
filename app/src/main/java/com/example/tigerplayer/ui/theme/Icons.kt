package com.example.tigerplayer.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import com.example.tigerplayer.R

/**
 * THE VANGUARD ICON ARCHIVE
 * A centralized dictionary using Rounded Material icons for high-visibility.
 */
object WitcherIcons {
    // --- NAVIGATION: The Paths ---
    val Home = Icons.Rounded.Home
    val Library = Icons.Rounded.LibraryMusic
    val Cloud = Icons.Rounded.CloudSync
    val Settings = Icons.Rounded.Settings
    val Menu = Icons.Rounded.Menu
    val Back = Icons.AutoMirrored.Rounded.ArrowBack
    val Forward = Icons.AutoMirrored.Rounded.ArrowForward
    val ChevronRight = Icons.AutoMirrored.Rounded.KeyboardArrowRight
    val ChevronLeft = Icons.AutoMirrored.Rounded.KeyboardArrowLeft
    val Expand = Icons.Rounded.KeyboardArrowUp
    val Collapse = Icons.Rounded.KeyboardArrowDown
    val Explore = Icons.Rounded.Explore

    // --- PLAYBACK: The Ritual Controls ---
    val Play = Icons.Rounded.PlayArrow
    val Pause = Icons.Rounded.Pause
    val Next = Icons.Rounded.SkipNext
    val Previous = Icons.Rounded.SkipPrevious
    val Shuffle = Icons.Rounded.Shuffle
    val Repeat = Icons.Rounded.Repeat
    val RepeatOne = Icons.Rounded.RepeatOne
    val FastForward = Icons.Rounded.FastForward
    val FastRewind = Icons.Rounded.FastRewind
    val Speed = Icons.Rounded.Speed
    val Stop = Icons.Rounded.Stop

    // --- MEDIA & METADATA: The Scrolls ---
    val Album = Icons.Rounded.Album
    val Artist = Icons.Rounded.Person
    val Group = Icons.Rounded.Group // For bands or collaborations
    val Playlist = Icons.AutoMirrored.Rounded.QueueMusic
    val Duration = Icons.Rounded.Timer
    val History = Icons.Rounded.History
    val Lyrics = Icons.Rounded.MicExternalOn // Perfect for "Vocal Rituals"
    val AudioTrack = Icons.Rounded.Audiotrack
    val Podcast = Icons.Rounded.Podcasts
    val Radio = Icons.Rounded.Radio

    // --- ACTIONS: The Combat Signs ---
    val Favorite = Icons.Rounded.Favorite
    val FavoriteBorder = Icons.Rounded.FavoriteBorder
    val Add = Icons.Rounded.Add
    val Create = Icons.Rounded.AddCircleOutline
    val Edit = Icons.Rounded.Edit
    val Delete = Icons.Rounded.DeleteOutline
    val Save = Icons.Rounded.Save
    val Download = Icons.Rounded.Download
    val Share = Icons.Rounded.Share
    val Options = Icons.Rounded.MoreVert
    val More = Icons.Rounded.MoreHoriz
    val Search = Icons.Rounded.Search
    val Close = Icons.Rounded.Close
    val Check = Icons.Rounded.CheckCircle
    val Refresh = Icons.Rounded.Refresh

    // --- SYSTEM & STATUS: The Bestiary ---
    val VolumeUp = Icons.AutoMirrored.Rounded.VolumeUp
    val VolumeDown = Icons.AutoMirrored.Rounded.VolumeDown
    val VolumeMute = Icons.AutoMirrored.Rounded.VolumeMute
    val Headphones = Icons.Rounded.Headphones
    val Bluetooth = Icons.Rounded.Bluetooth
    val Cast = Icons.Rounded.Cast
    val Wifi = Icons.Rounded.Wifi
    val Battery = Icons.Rounded.BatteryFull
    val Error = Icons.Rounded.ErrorOutline
    val Warning = Icons.Rounded.WarningAmber
    val Info = Icons.Rounded.Info

    // --- THEMATIC BADGES ---
    val LosslessBadge = Icons.Rounded.GraphicEq
    val HighRes = Icons.Rounded.NewReleases // Use for "24-bit" indicators
    val Explicit = Icons.Rounded.Explicit
    val Verified = Icons.Rounded.Verified

    // --- BRAND & FALLBACKS ---
    val DefaultAlbumArt = R.drawable.ic_tiger_logo
}