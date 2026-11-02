package mk.bosa.breakfast.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The scan icon from the Monochrome mocks (`M-Daily`): four rounded corner brackets around three bars,
 * drawn on a 24 grid with a 1.8 round stroke. Tint it with the icon's `tint`.
 */
val ScanBarcodeIcon: ImageVector by lazy {
    ImageVector.Builder(name = "ScanBarcode", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 8f); verticalLineTo(6f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
            horizontalLineToRelative(2f)
            moveTo(16f, 4f); horizontalLineToRelative(2f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
            verticalLineToRelative(2f)
            moveTo(20f, 16f); verticalLineToRelative(2f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
            horizontalLineToRelative(-2f)
            moveTo(8f, 20f); horizontalLineTo(6f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
            verticalLineToRelative(-2f)
            moveTo(8f, 9f); verticalLineToRelative(6f)
            moveTo(12f, 9f); verticalLineToRelative(6f)
            moveTo(16f, 9f); verticalLineToRelative(6f)
        }
        .build()
}
