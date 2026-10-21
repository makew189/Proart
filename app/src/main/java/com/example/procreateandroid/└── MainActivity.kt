package com.example.procreateandroid

import android.graphics.Bitmap
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.Path as NativePath
import android.os.Bundle
import android.content.ContentValues
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.launch

private data class BrushStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFB69CFF),
                    background = Color(0xFF17171D),
                    surface = Color(0xFF24242D)
                )
            ) {
                DrawingApp()
            }
        }
    }
}

@Composable
fun DrawingApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val strokes = remember { mutableStateListOf<BrushStroke>() }
    val redoStrokes = remember { mutableStateListOf<BrushStroke>() }

    var currentPoints by remember {
        mutableStateOf<List<Offset>>(emptyList())
    }

    var selectedColor by remember {
        mutableStateOf(Color.Black)
    }

    var brushSize by remember {
        mutableFloatStateOf(6f)
    }

    var isEraser by remember {
        mutableStateOf(false)
    }

    val palette = listOf(
        Color.Black,
        Color.White,
        Color.Red,
        Color(0xFFFF9800),
        Color.Yellow,
        Color.Green,
        Color.Cyan,
        Color.Blue,
        Color.Magenta,
        Color(0xFF795548)
    )

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val width = 2048
                    val height = 2048

                    val bitmap = Bitmap.createBitmap(
                        width,
                        height,
                        Bitmap.Config.ARGB_8888
                    )

                    val nativeCanvas = NativeCanvas(bitmap)
                    nativeCanvas.drawColor(android.graphics.Color.WHITE)

                    val paint = Paint().apply {
                        isAntiAlias = true
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }

                    strokes.forEach { stroke ->
                        if (stroke.points.isNotEmpty()) {
                            paint.color = stroke.color.toArgb()
                            paint.strokeWidth = stroke.width * 2f

                            val path = NativePath()
                            path.moveTo(
                                stroke.points.first().x * 2f,
                                stroke.points.first().y * 2f
                            )

                            stroke.points.drop(1).forEach { point ->
                                path.lineTo(point.x * 2f, point.y * 2f)
                            }

                            nativeCanvas.drawPath(path, paint)
                        }
                    }

                    context.contentResolver.openOutputStream(uri)?.use {
                        bitmap.compress(
                            Bitmap.CompressFormat.PNG,
                            100,
                            it
                        )
                    }

                    bitmap.recycle()

                    Toast.makeText(
                        context,
                        "Gambar berhasil diekspor",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Ekspor gagal: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF17171D))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Art Studio",
                color = Color.White,
                fontSize = 20.sp
            )

            Row {
                IconButton(
                    onClick = {
                        if (strokes.isNotEmpty()) {
                            redoStrokes.add(strokes.removeAt(strokes.lastIndex))
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.Undo,
                        contentDescription = "Undo",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = {
                        if (redoStrokes.isNotEmpty()) {
                            strokes.add(redoStrokes.removeAt(redoStrokes.lastIndex))
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.Redo,
                        contentDescription = "Redo",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = {
                        exportLauncher.launch("Artwork.png")
                    }
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = "Ekspor PNG",
                        tint = Color.White
                    )
                }
            }
        }

        // Kanvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
                .background(Color.White, RoundedCornerShape(4.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selectedColor, brushSize, isEraser) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPoints = listOf(offset)
                            },
                            onDrag = { change, _ ->
                                currentPoints = currentPoints + change.position
                            },
                            onDragEnd = {
                                if (currentPoints.isNotEmpty()) {
                                    strokes.add(
                                        BrushStroke(
                                            points = currentPoints,
                                            color = if (isEraser) {
                                                Color.White
                                            } else {
                                                selectedColor
                                            },
                                            width = if (isEraser) {
                                                brushSize * 2f
                                            } else {
                                                brushSize
                                            }
                                        )
                                    )
                                    redoStrokes.clear()
                                    currentPoints = emptyList()
                                }
                            },
                            onDragCancel = {
                                currentPoints = emptyList()
                            }
                        )
                    }
            ) {
                strokes.forEach { stroke ->
                    drawStroke(stroke)
                }

                if (currentPoints.isNotEmpty()) {
                    drawStroke(
                        BrushStroke(
                            points = currentPoints,
                            color = if (isEraser) Color.White else selectedColor,
                            width = if (isEraser) brushSize * 2f else brushSize
                        )
                    )
                }
            }
        }

        // Pemilih alat
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !isEraser,
                onClick = { isEraser = false },
                label = { Text("Brush") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Brush,
                        contentDescription = null
                    )
                }
            )

            FilterChip(
                selected = isEraser,
                onClick = { isEraser = true },
                label = { Text("Eraser") },
                leadingIcon = {
                    Icon(
                        Icons.Default.AutoFixNormal,
                        contentDescription = null
                    )
                }
            )

            Spacer(Modifier.weight(1f))

            TextButton(
                onClick = {
                    strokes.clear()
                    redoStrokes.clear()
                    currentPoints = emptyList()
                }
            ) {
                Text("Clear")
            }
        }

        // Ukuran kuas
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Ukuran kuas: ${brushSize.toInt()}",
                color = Color.White
            )

            Slider(
                value = brushSize,
                onValueChange = { brushSize = it },
                valueRange = 2f..40f
            )
        }

        // Palet warna
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            palette.forEach { color ->
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = color,
                    border = if (selectedColor == color) {
                        androidx.compose.foundation.BorderStroke(
                            3.dp,
                            Color(0xFFB69CFF)
                        )
                    } else {
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color.Gray
                        )
                    },
                    onClick = {
                        selectedColor = color
                        isEraser = false
                    }
                ) {}
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    stroke: BrushStroke
) {
    if (stroke.points.isEmpty()) return

    val path = Path().apply {
        moveTo(stroke.points.first().x, stroke.points.first().y)

        if (stroke.points.size == 1) {
            lineTo(
                stroke.points.first().x + 0.1f,
                stroke.points.first().y + 0.1f
            )
        } else {
            stroke.points.drop(1).forEach { point ->
                lineTo(point.x, point.y)
            }
        }
    }

    drawPath(
        path = path,
        color = stroke.color,
        style = Stroke(
            width = stroke.width,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}
