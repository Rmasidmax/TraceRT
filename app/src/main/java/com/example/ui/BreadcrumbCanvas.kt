package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.HikeDataPoint

@Composable
fun BreadcrumbCanvas(
    hikePoints: List<HikeDataPoint>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current

        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val width = size.width
            val height = size.height

            // 1. Draw safety high-contrast backup grid lines
            val gridCols = 8
            val gridRows = 8
            val gridColor = Color(0xFF1E1E1E)
            val outerBorderColor = Color(0xFF333333)

            // Horizontal grid
            for (i in 1 until gridRows) {
                val y = (height / gridRows) * i
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 2f
                )
            }
            // Vertical grid
            for (i in 1 until gridCols) {
                val x = (width / gridCols) * i
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 2f
                )
            }

            // Draw outer relative frame
            drawRect(
                color = outerBorderColor,
                topLeft = Offset(0f, 0f),
                size = size,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
            )

            // Empty state placeholder handled inside the Canvas (so we don't scale empty ranges)
            if (hikePoints.isEmpty()) {
                return@Canvas
            }

            // 2. Discover Latitude & Longitude extremes to automatically calculate bounds
            val latitudes = hikePoints.map { it.latitude }
            val longitudes = hikePoints.map { it.longitude }

            val minLat = latitudes.minOrNull() ?: 0.0
            val maxLat = latitudes.maxOrNull() ?: 0.0
            val minLng = longitudes.minOrNull() ?: 0.0
            val maxLng = longitudes.maxOrNull() ?: 0.0

            val latRange = maxLat - minLat
            val lngRange = maxLng - minLng

            // High padding to keep coordinate line off extreme card edges
            val padPx = with(density) { 36.dp.toPx() }
            val drawableWidth = width - padPx * 2
            val drawableHeight = height - padPx * 2

            val mappedOffsets = hikePoints.map { p ->
                val normX = if (lngRange > 0.0) (p.longitude - minLng) / lngRange else 0.5
                val normY = if (latRange > 0.0) (p.latitude - minLat) / latRange else 0.5

                val canvasX = padPx + (normX.toFloat() * drawableWidth)
                // Invert custom Y axis since latitude increases upwards on standard compasses
                val canvasY = padPx + ((1f - normY.toFloat()) * drawableHeight)

                Offset(canvasX, canvasY)
            }

            // 3. Draw Breadcrumb Polyline trail (Glowing High-Contrast Neon Green)
            if (mappedOffsets.size > 1) {
                for (i in 0 until mappedOffsets.size - 1) {
                    drawLine(
                        color = Color(0xFFA3E635), // Professional Polish Neon Green
                        start = mappedOffsets[i],
                        end = mappedOffsets[i + 1],
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 4. Draw Anchor Points (Start: Red, End: Pulsing/Glow Blue)
            if (mappedOffsets.isNotEmpty()) {
                val start = mappedOffsets.first()
                drawCircle(
                    color = Color(0xFFEF4444), // Professional Polish Red
                    radius = 12f,
                    center = start
                )
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = start
                )

                val last = mappedOffsets.last()
                drawCircle(
                    color = Color(0xFF38BDF8), // Professional Polish Blue
                    radius = 16f,
                    center = last
                )
                drawCircle(
                    color = Color.Black,
                    radius = 6f,
                    center = last
                )
                drawCircle(
                    color = Color.White,
                    radius = 3f,
                    center = last
                )
            }
        }

        // Overlay instructions if database tracker is empty
        if (hikePoints.isEmpty()) {
            Text(
                text = "NO COORD DATA RECORDED\nStart active hiking to plot trail",
                style = TextStyle(
                    color = Color(0xFF8E8E93),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Text(
                text = "AUTOSCALED COMPASS GRID (OFFLINE)",
                style = TextStyle(
                    color = Color(0xFF8E8E93).copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            )
        }
    }
}
