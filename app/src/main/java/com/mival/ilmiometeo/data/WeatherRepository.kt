package com.mival.ilmiometeo.data

import com.mival.ilmiometeo.model.WeatherItem
import com.mival.ilmiometeo.model.HourlyItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException

class WeatherRepository {


    suspend fun getHourlyForecast(city: String, dayLink: String): List<HourlyItem> = withContext(Dispatchers.IO) {
        val formattedCity = city.trim().replace(" ", "-")
        // Construct the detail URL. 
        // dayLink comes from getForecast, e.g. "https://www.ilmeteo.it/meteo/Pinerolo/domani"
        // If it's a full URL, use it. If relative, append.
        val url = if (dayLink.startsWith("http")) dayLink else "https://www.ilmeteo.it/meteo/$formattedCity/$dayLink"
        
        try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .get()
            
            val hourlyItems = mutableListOf<HourlyItem>()
            
            // Dynamic Column finding
            val headerRow = doc.select("table.weather_table thead tr th")
            var targetColIndex = -1
            var targetColLabel = "UR%"

            // Find index of "UR" or "perc"
            for ((index, th) in headerRow.withIndex()) {
                val text = th.text()
                if (text.contains("UR", ignoreCase = true)) {
                    targetColIndex = index
                    targetColLabel = "UR%"
                    break
                } else if (text.contains("perc", ignoreCase = true) || text.contains("T.p.", ignoreCase = true)) {
                    targetColIndex = index
                    targetColLabel = "TP°"
                    break
                }
            }

            // Selector: Parse ALL rows in the document finding those that look like weather rows.
            // This is the most robust way to find "overnight" rows that might be in different tables or outside the main table.
            // We rely on strict filters (hidden check, column count, time format) to ignore garbage.
            val rows = doc.select("tr")

            for (row in rows) {
                // EXCLUDE Hidden Rows
                if (row.attr("style").contains("display: none") || row.hasClass("hidden") || row.hasClass("ad_row") || row.hasClass("separator")) {
                    continue
                }

                val tds = row.select("td")
                
                // DATA INTEGRITY CHECK:
                if (tds.size < 6) continue

                // Ensure it's a data row (has time)
                val rawTime = tds.firstOrNull()?.text()?.trim() ?: ""
                
                // HYBRID Parsing Strategy:
                var validTime: String? = null
                
                // Case 1: HH:mm
                val colonMatch = Regex("""(\d{1,2}:\d{2})""").find(rawTime)
                if (colonMatch != null) {
                    validTime = colonMatch.value
                } else {
                    // Case 2: Just digits (1 or 2 digits), NO letters.
                    // Keep RAW format as requested (e.g. "1" remains "1", not "1:00")
                    if (rawTime.isNotEmpty() && rawTime.length <= 2 && rawTime.all { it.isDigit() }) {
                        validTime = rawTime
                    }
                }

                if (validTime != null) {
                    val time = validTime
                    val tds = row.select("td")
                    // Mapping based on inspection:
                    // 0: Time
                    // 1: Icon (span with class s-small)
                    // 2: Temp
                    // 3: Rain (Precip)
                    // 5: Wind (Vento)
                    
                    if (tds.size >= 6) {
                        // Icon extraction
                        // Logic based on data-simbolo with fallback
                        var iconUrl = "https://www.ilmeteo.it/img/meteo/s/coperto.png"
                        var weatherCode = 0
                        
                        val iconSpan = tds[1].select("span[data-simbolo]").firstOrNull()
                        if (iconSpan != null) {
                            val code = iconSpan.attr("data-simbolo").toIntOrNull() ?: 0
                            weatherCode = code
                            iconUrl = getIconUrlForCode(code)
                        } else {
                             // Fallback: Parse class ss-small{N}
                             val oldIconSpan = tds[1].select("span.s-small").firstOrNull()
                             if (oldIconSpan != null) {
                                 val classes = oldIconSpan.className()
                                 // Try to find ss-small + digits
                                 val match = Regex("""ss-small(\d+)""").find(classes)
                                 if (match != null) {
                                     weatherCode = match.groupValues[1].toInt()
                                 }
                                 
                                 // Retain URL fallback for daily cards (WeatherCard) which use iconUrl
                                 if (classes.contains("sole") || classes.contains("sereno")) iconUrl = "https://www.ilmeteo.it/img/meteo/s/sole.png"
                                 else if (classes.contains("pioggia")) iconUrl = "https://www.ilmeteo.it/img/meteo/s/pioggia.png"
                                 else if (classes.contains("neve")) iconUrl = "https://www.ilmeteo.it/img/meteo/s/neve.png"
                                 else if (classes.contains("nuvol")) iconUrl = "https://www.ilmeteo.it/img/meteo/s/nuvoloso.png"
                                 else if (classes.contains("nebbia")) iconUrl = "https://www.ilmeteo.it/img/meteo/s/nebbia.png"
                             }
                        }
                        
                        val temp = tds[2].text()
                        
                        // Precip parsing
                        val precipTd = tds[3]
                        val rain = precipTd.text()
                        var rainType = "none"
                        if (precipTd.select(".fiocco").isNotEmpty()) {
                            rainType = "neve"
                        } else if (precipTd.select(".precontainer").isNotEmpty() || rain.contains("mm") || rain.contains("pioggia")) {
                             rainType = "pioggia"
                        }

                        val wind = tds[5].text()
                        
                        // Dynamic Target Column Extraction
                        var lastColValue = "--"
                        
                        if (targetColIndex != -1 && tds.size > targetColIndex) {
                            lastColValue = tds[targetColIndex].text()
                            if (targetColLabel == "UR%" && !lastColValue.contains("%")) lastColValue += "%"
                            if (targetColLabel == "TP°" && !lastColValue.contains("°")) lastColValue += "°"
                        } else {
                            // Fallback if header parsing failed: keep looking at index 9 (standard) usually
                            if (tds.size > 9) {
                                lastColValue = tds[9].text() + "%" // Guessing UR
                            }
                        }
                        
                        // Quota/Snow & Visibility (Best effort / Optional)
                        val snowLevel = "--"
                        val airQuality = "--"
                        val visibility = "--"

                        hourlyItems.add(HourlyItem(time, iconUrl, weatherCode, temp, rain, rainType, wind, snowLevel, airQuality, visibility, lastColValue, targetColLabel))
                    }
                }
            }
            hourlyItems
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun getIconUrlForCode(code: Int): String {
        // Mapping based on common ilMeteo codes
        return when (code) {
             1, 10 -> "https://www.ilmeteo.it/img/meteo/s/sole.png" // Sole / Sereno
             2, 110 -> "https://www.ilmeteo.it/img/meteo/s/luna.png" // Sereno notte (?) or varies
             3, 4, 11 -> "https://www.ilmeteo.it/img/meteo/s/nuvoloso.png"
             13, 111, 113 -> "https://www.ilmeteo.it/img/meteo/s/neve.png"
             5, 12, 112 -> "https://www.ilmeteo.it/img/meteo/s/pioggia.png"
             19, 20 -> "https://www.ilmeteo.it/img/meteo/s/temporale.png"
             23, 24 -> "https://www.ilmeteo.it/img/meteo/s/nebbia.png"
             else -> "https://www.ilmeteo.it/img/meteo/s/coperto.png"
        }
    }

    suspend fun getForecast(city: String): List<WeatherItem> = withContext(Dispatchers.IO) {
        val formattedCity = city.trim().replace(" ", "-")
        val url = "https://www.ilmeteo.it/meteo/$formattedCity"
        
        try {
            // Use a recent real User-Agent to avoid 403
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .get()
            
            val items = mutableListOf<WeatherItem>()

            // Strategy 1: Parse the "Next Days" horizontal list (often found on main page)
            // Selector: .forecast_day_selector__list > li
            val dayList = doc.select(".forecast_day_selector__list li.forecast_day_selector__list__item")
            
            if (dayList.isNotEmpty()) {
                for (li in dayList) {
                    // Date/Day: The link usually contains spans.
                    val anchor = li.select("a")
                    val linkText = anchor.text() // e.g. "Lun 25" or "Meteo giornaliero"
                    val href = anchor.attr("href") // e.g. "/meteo/Pinerolo/domani"
                    
                    // Filter: Must look like a date (Day Name + Number)
                    // Heuristic: Check if it contains a digit
                    if (!linkText.any { it.isDigit() }) continue
                    
                    // Icon extraction
                    // Logic based on data-simbolo which is usually on a span inside the link
                    var iconUrl = "https://www.ilmeteo.it/img/meteo/s/coperto.png"
                    var weatherCode = 0
                    
                    // Look for data-simbolo on ANY span inside the li/anchor
                    val iconSpan = li.select("[data-simbolo]").firstOrNull()
                    
                    if (iconSpan != null) {
                        val codeAttr = iconSpan.attr("data-simbolo")
                        if (codeAttr.isNotEmpty()) {
                             weatherCode = codeAttr.toIntOrNull() ?: 0
                        }
                    }
                    
                    // Fallback: If no data-simbolo, try regex on class names
                    if (weatherCode == 0) {
                         val spanWithClass = li.select("span[class*='ss-small']").firstOrNull()
                         if (spanWithClass != null) {
                             val classes = spanWithClass.className()
                             // Try matching ss-small{N}
                             val match = Regex("""ss-small(\d+)""").find(classes)
                             if (match != null) {
                                 weatherCode = match.groupValues[1].toInt()
                             }
                         }
                    }
                    
                    // Set URL for fallback legacy mode
                    if (weatherCode > 0) {
                        iconUrl = getIconUrlForCode(weatherCode)
                    } else {
                         // Legacy heuristic
                         val anyIconClass = li.select("span[class*='s-small']").firstOrNull()?.className() ?: ""
                         if (anyIconClass.contains("sole") || anyIconClass.contains("sereno")) iconUrl = "https://www.ilmeteo.it/img/meteo/s/sole.png"
                         else if (anyIconClass.contains("pioggia")) iconUrl = "https://www.ilmeteo.it/img/meteo/s/pioggia.png"
                         else if (anyIconClass.contains("nuvol")) iconUrl = "https://www.ilmeteo.it/img/meteo/s/nuvoloso.png"
                         else if (anyIconClass.contains("nebbia")) iconUrl = "https://www.ilmeteo.it/img/meteo/s/nebbia.png"
                    }
                    
                    // Check for img tag if all else fails
                    if (weatherCode == 0 && iconUrl.contains("coperto.png")) {
                         val imgTag = li.select("img").attr("src")
                         if (imgTag.isNotEmpty()) {
                             iconUrl = if (imgTag.startsWith("//")) "https:$imgTag" else imgTag
                         }
                    }

                    // Temps: .forecast_day_selector__list__item__link__values
                    val tempText = li.select(".forecast_day_selector__list__item__link__values").text() // "3° 9°"
                    val parts = tempText.split(" ")
                    val minT = parts.firstOrNull() ?: "--"
                    val maxT = parts.lastOrNull() ?: "--"
                    
                    if (items.size < 7) {
                        items.add(WeatherItem(
                            day = linkText.take(6), // Truncate to "Lun 25"
                            date = linkText,
                            iconUrl = iconUrl,
                            weatherCode = weatherCode,
                            minTemp = minT,
                            maxTemp = maxT,
                            description = linkText, // Short description
                            link = href
                        ))
                    }
                }
            } else {
                // Strategy 2: Fallback to table rows (legacy or 15-day view structure)
                // Try selecting rows that definitely have weather data
                val rows = doc.select("tr")
                val processedDays = mutableSetOf<String>()
                
                for (row in rows) {
                    val text = row.text()
                    if (text.contains("°") && (text.contains("Lun") || text.contains("Mar") || text.contains("Mer") || text.contains("Gio") || text.contains("Ven") || text.contains("Sab") || text.contains("Dom"))) {
                        
                        val firstCell = row.select("td").firstOrNull()?.text() ?: ""
                        val dayKey = firstCell.split(" ").firstOrNull() ?: ""
                        
                        if (dayKey.isNotEmpty() && !processedDays.contains(dayKey) && items.size < 7) {
                             val iconEl = row.select("img[src*='meteo']").firstOrNull()
                             val iconUrl = if (iconEl != null) "https:" + iconEl.attr("src").replace("https:", "") else "https://www.ilmeteo.it/img/meteo/s/sole.png"
                             
                             // Temp extraction heuristic
                             val cells = row.select("td")
                             val tempTexts = cells.map { it.text() }.filter { it.contains("°") }
                             val minT = tempTexts.firstOrNull() ?: "--"
                             val maxT = tempTexts.lastOrNull() ?: "--"
                             
                             items.add(WeatherItem(firstCell, firstCell, iconUrl, 0, minT, maxT, "Previsioni", ""))
                             processedDays.add(dayKey)
                        }
                    }
                }
            }

            items
        } catch (e: Exception) {
            e.printStackTrace()
            // Return a dummy error item so the user sees something happened
            listOf(WeatherItem("Errore", "Impossibile scaricare", "", 0, "--", "--", e.message ?: "Unknown error", ""))
        }
    }
}
