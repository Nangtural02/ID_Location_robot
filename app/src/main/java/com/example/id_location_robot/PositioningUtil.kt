package com.example.id_location_admin

import android.content.res.Configuration
import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.VectorProperty
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.id_location_robot.R
import com.example.id_location_robot.ui.theme.ID_Location_robotTheme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@Composable
fun CoordinatePlane(
    anchorList: List<Point>,
    pointsList: List<Point>,
    isDanger: Boolean,
    distanceList: List<Float>? = null,
    displayDistanceCircle: Boolean = false,
    toggleGrid: Boolean = true,
    toggleAxis: Boolean = true,
    scale: Float = 1f,  // 줌 값 추가
    offsetX: Float = 0f,  // X축 오프셋 추가
    offsetY: Float = 0f   // Y축 오프셋 추가
) {
    val colors = listOf(
        Color.Magenta,
        Color.Green,
        Color.Blue,
        Color.DarkGray,
        Color.Cyan,
        Color.Gray,
        Color.LightGray
    )
    var max = 10f
    var min = -1f

    if (anchorList.isNotEmpty()) {
        max = anchorList.maxOf { ceil(max(it.x, it.y)) }
        min = anchorList.minOf { floor(min(it.x, it.y)) }
    }
    val infiniteTransition = rememberInfiniteTransition()
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500), // 0.5초 간격으로 깜빡임
            repeatMode = RepeatMode.Reverse
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)  // Set the height to match the width
            .padding(6.dp),
        colors = CardDefaults.cardColors(Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(modifier = Modifier.padding(5.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()
                .background(Color.Transparent)) {
                val width = size.width
                val localMax: Float = if (min < 0) max - min else max
                val localMin: Float = if (min < 0) 0f else min
                val localAxis: Float = if (min < 0) -min else 0f
                val baseScale = width / (max - min)
                val finalScale = baseScale * scale  // scale 적용
                val originX = (localMin + localAxis) * finalScale + offsetX  // offset 적용
                val originY = (localMax - localAxis) * finalScale + offsetY  // offset 적용
                val step = (max - min).toInt()

                if(toggleGrid) {
                    for (i in 0..step) {
                        val x: Float = (localMin + i) * finalScale + offsetX
                        val y: Float = (localMax - i) * finalScale + offsetY
                        drawLine(
                            color = Color.LightGray,
                            start = Offset(x, 0f),
                            end = Offset(x, width),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = Color.LightGray,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                // Draw X-axis and Y-axis
                if(toggleAxis) {
                    drawLine(
                        color = Color.Gray,
                        start = Offset(0f, originY),
                        end = Offset(width, originY),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = Color.Gray,
                        start = Offset(originX, 0f),
                        end = Offset(originX, width),
                        strokeWidth = 2.dp.toPx()
                    )
                }

                if (anchorList.isNotEmpty()) {
                    anchorList.forEach { point ->
                        val scaledX = point.x * finalScale + offsetX  // scale과 offset 적용
                        val scaledY = point.y * finalScale + offsetY  // scale과 offset 적용
                        drawCircle(
                            color = Color.Red,
                            radius = 3.dp.toPx(),
                            center = Offset(
                                x = scaledX + originX,
                                y = originY - scaledY
                            )
                        )

                        // Draw distance circles if needed
                        if (distanceList != null && distanceList.size <= anchorList.size && displayDistanceCircle) {
                            if(distanceList.size > anchorList.indexOf(point)) {
                                drawCircle(
                                    color = Color.LightGray,
                                    radius = (distanceList[anchorList.indexOf(point)] * finalScale),
                                    center = Offset(
                                        x = scaledX + originX,
                                        y = originY - scaledY
                                    ),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }

                    // Draw points with different colors for each list
                    pointsList.forEachIndexed { index, point ->
                        val color = colors.getOrElse(index) { Color.Black }
                        val scaledX = point.x * finalScale + offsetX  // scale과 offset 적용
                        val scaledY = point.y * finalScale + offsetY  // scale과 offset 적용
                        drawCircle(
                            color = color,
                            radius = 5.dp.toPx(),
                            center = Offset(
                                x = scaledX + originX,
                                y = originY - scaledY
                            )
                        )

                        // 위험 상태일 경우, 깜빡이는 큰 붉은 원 추가
                        if (isDanger) {
                            drawCircle(
                                color = Color.Red.copy(alpha = alphaAnim), // 반투명한 붉은 원
                                radius = 30.dp.toPx(), // 더 큰 원
                                center = Offset(
                                    x = scaledX + originX,
                                    y = originY - scaledY
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Domyon(
    anchorList: List<Point>,
    pointsList: List<Point>,
    isDanger: Boolean = false,
    distanceList: List<Float>? = null,
    displayDistanceCircle: Boolean = false,
    toggleGrid: Boolean = true,
    toggleAxis: Boolean = true,
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    backgroundImageResId: Int? = null,
    pixelsPerMeter: Float = 100f,
    originOffsetX: Float = 0f,
    originOffsetY: Float = 0f,
    isHorizontal: Boolean = true // 가로 방향 설정
) {
    val colors = listOf(
        Color.Magenta,
        Color.Green,
        Color.Blue,
        Color.DarkGray,
        Color.Cyan,
        Color.Gray,
        Color.LightGray
    )

    val backgroundImage = backgroundImageResId?.let { resId ->
        ImageBitmap.imageResource(id = resId)
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(20f / 9f), // 20:9 비율 설정
        colors = CardDefaults.cardColors(Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 배경 이미지 표시
            backgroundImage?.let { image ->
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width // 2325.0
                val canvasHeight = size.height // 1046.0

                val adjustedPixelsPerMeterX = canvasWidth / 24f // X축 최대값 기준 조정
                val adjustedPixelsPerMeterY = canvasHeight / 10.8f // Y축 최대값 기준 조정

                val finalScaleX = adjustedPixelsPerMeterX * scale
                val finalScaleY = adjustedPixelsPerMeterY * scale

                // 캔버스에서 좌표의 원점 설정 (왼쪽 하단)
                val originX = originOffsetX + offsetX
                val originY = canvasHeight - originOffsetY + offsetY

                println("Canvas Width: $canvasWidth, Canvas Height: $canvasHeight")
                println("Adjusted Scale X: $finalScaleX, Adjusted Scale Y: $finalScaleY")

                // 포인트 그리기
                pointsList.forEachIndexed { index, point ->
                    val color = colors.getOrElse(index) { Color.Black }
                    val scaledX = point.x * finalScaleX
                    val scaledY = point.y * finalScaleY

                    val canvasX = originX + scaledX
                    val canvasY = originY - scaledY

                    drawCircle(
                        color = color,
                        radius = 5.dp.toPx(),
                        center = Offset(canvasX, canvasY)
                    )
                }
            }
        }
    }
}


@Preview(showBackground = true, widthDp = 800, heightDp = 360,
    uiMode = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO or Configuration.ORIENTATION_LANDSCAPE,
    name = "GaRo")
@Composable
fun DomyonGaroPreview(){
    ID_Location_robotTheme() {
        Domyon(anchorList = listOf(),
            pointsList =  listOf(Point(24f,0.5f),),
            backgroundImageResId = R.drawable.yulgok_background,
            isHorizontal = false
        )
    }
}
@Preview(showBackground = true, widthDp = 360, heightDp = 800,
    uiMode = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO or Configuration.ORIENTATION_LANDSCAPE)
@Composable
fun DomyonSeroPreview(){
    ID_Location_robotTheme() {
        Domyon(anchorList = listOf(),
            pointsList =  listOf(Point(3f,3f),),
            backgroundImageResId = R.drawable.yulgok_background,
            isHorizontal = true
        )
    }
}