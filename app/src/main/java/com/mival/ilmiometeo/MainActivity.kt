package com.mival.ilmiometeo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.mival.ilmiometeo.data.WeatherRepository
import com.mival.ilmiometeo.model.WeatherItem
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource


@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme() // Force dark theme for better contrast with backgrounds
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WeatherScreen()
                }
            }
        }
    }
}

@Composable
fun WeatherScreen() {
    var city by remember { mutableStateOf("") }
    var weatherList by remember { mutableStateOf<List<WeatherItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Detail View State
    var selectedDay by remember { mutableStateOf<WeatherItem?>(null) }
    var hourlyList by remember { mutableStateOf<List<com.mival.ilmiometeo.model.HourlyItem>>(emptyList()) }
    var isDetailLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val repository = remember { WeatherRepository() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Persistence
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("weather_prefs", android.content.Context.MODE_PRIVATE) }
    
    // Auto-load last city
    LaunchedEffect(Unit) {
        val lastCity = prefs.getString("last_city", "")
        if (!lastCity.isNullOrBlank()) {
            city = lastCity
            // Trigger search
            isLoading = true
             scope.launch {
                try {
                    val result = repository.getForecast(lastCity)
                    if (result.isNotEmpty()) weatherList = result
                    else errorMessage = "Previsioni non disponibili."
                } catch (e: Exception) {
                     errorMessage = e.message
                } finally { isLoading = false }
            }
        }
    }

    // Background Logic
    // Initial screen (empty list) -> Sunny Image
    // Data loaded -> Gradient
    val showImage = weatherList.isEmpty()
    
    val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val isNight = currentHour < 6 || currentHour >= 19
    
    // Gradient definitions
    val nightGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
    )
    val dayGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF2980B9), Color(0xFF6DD5FA), Color(0xFFFFFFFF))
    )
    
    val activeGradient = if (isNight) nightGradient else dayGradient

    Box(
        modifier = Modifier.fillMaxSize()
            .background(activeGradient)
    ) {


        // Content
        if (selectedDay != null) {
            DetailScreen(
                city = city,
                dayItem = selectedDay!!,
                hourlyItems = hourlyList,
                isLoading = isDetailLoading,
                isNight = isNight,
                onClose = { selectedDay = null; hourlyList = emptyList() }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo at the top
                Spacer(modifier = Modifier.height(60.dp))
                
                Box(contentAlignment = Alignment.Center) {
                    // Stroke/Outline
                    Text(
                        text = "ilMioMeteo",
                        style = MaterialTheme.typography.displayMedium.copy(
                            drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(miter = 10f, width = 8f)
                        ),
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold
                    )
                    // Fill
                    Text(
                        text = "ilMioMeteo",
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.weight(1f)) // Push content to center

                // Search Bar in center
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("Inserisci Città", color = Color.White) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.7f),
                        focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.3f)
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        if (city.isNotBlank()) {
                            // Save to prefs
                            prefs.edit().putString("last_city", city).apply()

                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val result = repository.getForecast(city)
                                    if (result.isNotEmpty()) {
                                        weatherList = result
                                    } else {
                                        errorMessage = "Nessun dato trovato o errore di connessione."
                                        weatherList = emptyList()
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Errore: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    })
                )
                
                Spacer(modifier = Modifier.weight(1.5f)) // Balance bottom


                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodyLarge,
                         modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(weatherList) { item ->
                            WeatherCard(item) {
                                if (item.link.isNotEmpty()) {
                                    selectedDay = item
                                    isDetailLoading = true
                                    scope.launch {
                                        hourlyList = repository.getHourlyForecast(city, item.link)
                                        isDetailLoading = false
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailScreen(
    city: String,
    dayItem: WeatherItem,
    hourlyItems: List<com.mival.ilmiometeo.model.HourlyItem>,
    isLoading: Boolean,
    onClose: () -> Unit,
    isNight: Boolean
) {
    val formattedCity = city.lowercase().replaceFirstChar { it.uppercase() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = onClose,
            // Darker button for better contrast
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f), contentColor = Color.White)
        ) {
            Text("← Torna indietro")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = buildAnnotatedString {
                append("Previsioni orarie per ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                    append(dayItem.day)
                }
                append(" a ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                    append(formattedCity)
                }
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (isNight) Color.White else Color.Black,
            modifier = Modifier.background(if (isNight) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha=0.5f), RoundedCornerShape(4.dp)).padding(8.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = if (isNight) Color.White else Color.Black)
            }
        } else {
            val lastColLabel = hourlyItems.firstOrNull()?.humidityLabel ?: "UR%"
            
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
             // Header Row
                item {
                    Row(
                         modifier = Modifier
                         .fillMaxWidth()
                         .background(Color.Black.copy(alpha=0.6f))
                         .padding(vertical = 8.dp, horizontal = 8.dp),
                         horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ora", color = Color.White, modifier = Modifier.width(40.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text("Tempo", color = Color.White, modifier = Modifier.width(45.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontSize = 10.sp) 
                         Text("°C", color = Color.White, modifier = Modifier.width(40.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text("Precip.", color = Color.White, modifier = Modifier.width(60.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text("Vento", color = Color.White, modifier = Modifier.width(60.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text(lastColLabel, color = Color.White, modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }

                items(hourlyItems) { item ->
                    HourlyCard(item)
                }
            }
        }
    }
}

@Composable
fun HourlyCard(item: com.mival.ilmiometeo.model.HourlyItem) {
     Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
    ) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Ora (Match Header: 40.dp)
            Text(text = item.time, modifier = Modifier.width(40.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.Black)
            
            // Icona Tempo (Match Header Tempo: 45.dp)
            // Centered in column
            Box(modifier = Modifier.width(45.dp), contentAlignment = Alignment.CenterStart) {
                SpriteIcon(code = item.weatherCode, modifier = Modifier.size(40.dp)) 
            }

            // Temp (Match Header °C: 40.dp)
            Text(text = "${item.temp}°", modifier = Modifier.width(40.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.Black)

            // Pioggia (Match Header Prec: 60.dp)
            Row(modifier = Modifier.width(60.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = item.rain, style = MaterialTheme.typography.bodySmall, color = Color.Black, fontSize = 11.sp, maxLines = 1)
                 if (item.rainType != "none") {
                     Text(if(item.rainType == "neve") "❄️" else "💧", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                 }
            }

            // Vento (Match Header Vento: 60.dp)
            Text(text = item.wind, modifier = Modifier.width(60.dp), style = MaterialTheme.typography.bodySmall, color = Color.Black, fontSize = 10.sp, maxLines = 2)

            // UR (Match Header UR: 30.dp)
            Text(text = item.humidity, modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, color = Color.DarkGray, fontSize = 11.sp)
        }
    }
}

@Composable
fun DetailBadge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.DarkGray,
        modifier = Modifier.background(Color.LightGray.copy(alpha=0.3f), RoundedCornerShape(4.dp)).padding(horizontal=4.dp, vertical=2.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherCard(item: WeatherItem, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icona
            // Use SpriteIcon if code is available, else fallback to URL logic
            if (item.weatherCode > 0) {
                 SpriteIcon(code = item.weatherCode, modifier = Modifier.size(80.dp))
            } else {
                Image(
                    painter = rememberAsyncImagePainter(item.iconUrl),
                    contentDescription = item.description,
                    modifier = Modifier.size(80.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.day, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = item.maxTemp, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(text = item.minTemp, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        }
    }
}

@Composable
fun SpriteIcon(code: Int, modifier: Modifier = Modifier) {
    // Sprite total size: ~3000x200 (approx)
    // Icon size: 60x60
    // Step X: 61px
    
    // Manual mapping derived from site CSS
    // Format: -Xpx -Ypx
    val (xPx, yPx) = when (code) {
        // Day
        1 -> 0 to 0
        2 -> -61 to 0
        3 -> -122 to 0
        4 -> -183 to 0
        5 -> -305 to 0
        6 -> -366 to 0
        7 -> -427 to 0
        8 -> -488 to 0
        9 -> -549 to 0
        10 -> -610 to 0
        11 -> -671 to 0
        12 -> -732 to 0
        13 -> -793 to 0
        14 -> -854 to 0
        15 -> -915 to 0
        16 -> -976 to 0
        17 -> -1037 to 0
        18 -> -1098 to 0
        19 -> -461 to -60
        20 -> -523 to -60
        21 -> -585 to -60
        22 -> -645 to -60
        23 -> -705 to -60
        24 -> -765 to -60
        25 -> -122 to -120
        26 -> -61 to -120
        27 -> 0 to -120
        28 -> -488 to -120
        29 -> -427 to -120
        30 -> -183 to -120
        31 -> -305 to -120
        32 -> -244 to -120
        33 -> -366 to -120
        34 -> -549 to -120
        // Night
        101 -> -1159 to 0
        102 -> -1220 to 0
        103 -> -1281 to 0
        104 -> -1342 to 0
        105 -> -1464 to 0
        106 -> -1525 to 0
        107 -> -1586 to 0
        108 -> -1647 to 0
        109 -> -1708 to 0
        110 -> -1769 to 0
        111 -> -1830 to 0
        112 -> -1891 to 0
        113 -> -1952 to 0
        114 -> 0 to -60
        115 -> -61 to -60
        116 -> -122 to -60
        117 -> -183 to -60
        118 -> -244 to -60
        119 -> -401 to -60
        120 -> -523 to -60
        125 -> -732 to -120
        126 -> -671 to -120
        127 -> -610 to -120
        128 -> -1098 to -120
        129 -> -1037 to -120
        130 -> -793 to -120
        131 -> -915 to -120
        132 -> -854 to -120
        133 -> -976 to -120
        134 -> -1159 to -120
        
        // Default to a question mark or clear
        else -> 0 to 0
    }
    
    // Icon size on sprite is 60x60
    val iconSize = 60
    
    // We must invert logic if the map has negative values (CSS background-position)
    // background-position: -61px 0px means the image is shifted LEFT by 61px, 
    // so we need to read from x=61 in the source image.
    val xOffset = kotlin.math.abs(xPx)
    val yOffset = kotlin.math.abs(yPx)
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageBitmap = androidx.compose.ui.graphics.ImageBitmap.imageResource(context.resources, R.drawable.weather_sprite)
    
    androidx.compose.foundation.Canvas(modifier = modifier) {
        drawImage(
            image = imageBitmap,
            srcOffset = androidx.compose.ui.unit.IntOffset(xOffset, yOffset),
            srcSize = androidx.compose.ui.unit.IntSize(iconSize, iconSize),
            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
        )
    }
}
