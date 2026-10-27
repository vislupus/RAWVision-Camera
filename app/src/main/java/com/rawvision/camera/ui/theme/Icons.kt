package com.rawvision.camera.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rawvision.camera.R

/**
 * Lucide icons (https://lucide.dev, ISC licence) converted to VectorDrawables.
 * Everything in the UI goes through [LucideIcon] so icon size, stroke and
 * tint stay consistent.
 */
object Lucide {
    val Camera = R.drawable.lucide_camera
    val Images = R.drawable.lucide_images
    val Image = R.drawable.lucide_image
    val Settings = R.drawable.lucide_settings
    val Sliders = R.drawable.lucide_sliders_horizontal
    val SlidersVertical = R.drawable.lucide_sliders_vertical
    val Aperture = R.drawable.lucide_aperture
    val Sun = R.drawable.lucide_sun
    val SunDim = R.drawable.lucide_sun_dim
    val SunMedium = R.drawable.lucide_sun_medium
    val SunMoon = R.drawable.lucide_sun_moon
    val Moon = R.drawable.lucide_moon
    val Monitor = R.drawable.lucide_monitor
    val Palette = R.drawable.lucide_palette
    val Type = R.drawable.lucide_type
    val FontSize = R.drawable.lucide_a_large_small
    val User = R.drawable.lucide_user
    val Copyright = R.drawable.lucide_copyright
    val MapPin = R.drawable.lucide_map_pin
    val Info = R.drawable.lucide_info
    val Trash = R.drawable.lucide_trash
    val Share = R.drawable.lucide_share_2
    val Download = R.drawable.lucide_download
    val RotateCcw = R.drawable.lucide_rotate_ccw
    val RotateCw = R.drawable.lucide_rotate_cw
    val Undo = R.drawable.lucide_undo_2
    val Redo = R.drawable.lucide_redo_2
    val Check = R.drawable.lucide_check
    val CheckCheck = R.drawable.lucide_check_check
    val X = R.drawable.lucide_x
    val ChevronLeft = R.drawable.lucide_chevron_left
    val ChevronRight = R.drawable.lucide_chevron_right
    val ChevronDown = R.drawable.lucide_chevron_down
    val ChevronUp = R.drawable.lucide_chevron_up
    val ArrowLeft = R.drawable.lucide_arrow_left
    val ArrowLeftRight = R.drawable.lucide_arrow_left_right
    val ArrowUpDown = R.drawable.lucide_arrow_up_down
    val Zap = R.drawable.lucide_zap
    val ZapOff = R.drawable.lucide_zap_off
    val Flashlight = R.drawable.lucide_flashlight
    val Grid = R.drawable.lucide_grid_3x3
    val Focus = R.drawable.lucide_focus
    val Crosshair = R.drawable.lucide_crosshair
    val ZoomIn = R.drawable.lucide_zoom_in
    val ZoomOut = R.drawable.lucide_zoom_out
    val SwitchCamera = R.drawable.lucide_switch_camera
    val Timer = R.drawable.lucide_timer
    val TimerOff = R.drawable.lucide_timer_off
    val Circle = R.drawable.lucide_circle
    val CircleDot = R.drawable.lucide_circle_dot
    val CircleDashed = R.drawable.lucide_circle_dashed
    val CircleCheck = R.drawable.lucide_circle_check
    val CircleOff = R.drawable.lucide_circle_off
    val Star = R.drawable.lucide_star
    val Heart = R.drawable.lucide_heart
    val Save = R.drawable.lucide_save
    val Folder = R.drawable.lucide_folder
    val FolderOpen = R.drawable.lucide_folder_open
    val Database = R.drawable.lucide_database
    val FileText = R.drawable.lucide_file_text
    val FileImage = R.drawable.lucide_file_image
    val Crop = R.drawable.lucide_crop
    val Contrast = R.drawable.lucide_contrast
    val Droplet = R.drawable.lucide_droplet
    val Droplets = R.drawable.lucide_droplets
    val Thermometer = R.drawable.lucide_thermometer
    val Sparkles = R.drawable.lucide_sparkles
    val Wand = R.drawable.lucide_wand_sparkles
    val Eye = R.drawable.lucide_eye
    val EyeOff = R.drawable.lucide_eye_off
    val MoreVertical = R.drawable.lucide_ellipsis_vertical
    val More = R.drawable.lucide_ellipsis
    val Search = R.drawable.lucide_search
    val LayoutGrid = R.drawable.lucide_layout_grid
    val List = R.drawable.lucide_list
    val Pencil = R.drawable.lucide_pencil
    val Lock = R.drawable.lucide_lock
    val LockOpen = R.drawable.lucide_lock_open
    val Maximize = R.drawable.lucide_maximize
    val Minimize = R.drawable.lucide_minimize
    val Mountain = R.drawable.lucide_mountain
    val Flower = R.drawable.lucide_flower
    val Cloud = R.drawable.lucide_cloud
    val CloudSun = R.drawable.lucide_cloud_sun
    val Lightbulb = R.drawable.lucide_lightbulb
    val Lamp = R.drawable.lucide_lamp
    val Activity = R.drawable.lucide_activity
    val Histogram = R.drawable.lucide_chart_column
    val Cpu = R.drawable.lucide_cpu
    val Smartphone = R.drawable.lucide_smartphone
    val HardDrive = R.drawable.lucide_hard_drive
    val Shield = R.drawable.lucide_shield
    val Loader = R.drawable.lucide_loader_circle
    val Alert = R.drawable.lucide_triangle_alert
    val Copy = R.drawable.lucide_copy
    val ExternalLink = R.drawable.lucide_external_link
    val RefreshCw = R.drawable.lucide_refresh_cw
    val Compass = R.drawable.lucide_compass
    val Globe = R.drawable.lucide_globe
    val Clock = R.drawable.lucide_clock
    val Calendar = R.drawable.lucide_calendar
    val Tag = R.drawable.lucide_tag
    val Scan = R.drawable.lucide_scan
    val Ruler = R.drawable.lucide_ruler
    val Move = R.drawable.lucide_move
    val Layers = R.drawable.lucide_layers
    val Languages = R.drawable.lucide_languages
    val Paintbrush = R.drawable.lucide_paintbrush
    val Gauge = R.drawable.lucide_gauge
    val Flame = R.drawable.lucide_flame
    val Snowflake = R.drawable.lucide_snowflake
    val Plus = R.drawable.lucide_plus
    val Minus = R.drawable.lucide_minus
    val BookOpen = R.drawable.lucide_book_open
    val Film = R.drawable.lucide_film
    val Menu = R.drawable.lucide_menu
    val House = R.drawable.lucide_house
    val SquarePen = R.drawable.lucide_square_pen
    val Send = R.drawable.lucide_send
    val Blend = R.drawable.lucide_blend
    val Spline = R.drawable.lucide_spline
    val Square = R.drawable.lucide_square
    val SquareCheck = R.drawable.lucide_square_check
    val ChevronsUpDown = R.drawable.lucide_chevrons_up_down
    val Repeat = R.drawable.lucide_repeat
    val Bolt = R.drawable.lucide_bolt
    val Hand = R.drawable.lucide_hand
    val Play = R.drawable.lucide_play
    val Waypoints = R.drawable.lucide_waypoints
    val Eraser = R.drawable.lucide_eraser
    val Bug = R.drawable.lucide_bug
    val Scale3d = R.drawable.lucide_scale_3d
    val Rotate3d = R.drawable.lucide_rotate_3d
    val Brush = R.drawable.lucide_brush
    val Maximize2 = R.drawable.lucide_maximize_2
    val GalleryEnd = R.drawable.lucide_gallery_horizontal_end
    val SunSnow = R.drawable.lucide_sun_snow
    val CircleSlash = R.drawable.lucide_circle_slash_2
}

@Composable
fun LucideIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
