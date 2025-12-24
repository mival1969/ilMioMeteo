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
            
            // Selector: table.weather_table tbody tr (exclude ad_row, separator)
            val rows = doc.select("table.weather_table tbody tr:not(.ad_row):not(.separator)")
            
            for (row in rows) {
                // Ensure it's a data row (has time)
                val time = row.select("td").firstOrNull()?.text() ?: ""
                
                // Heuristic: valid time rows usually start with digits (e.g. 01:00 or 1)
                if (time.isNotEmpty() && (time[0].isDigit())) {
                    val tds = row.select("td")
                    // Mapping based on inspection:
                    // 0: Time
                    // 1: Icon (span with class s-small)
                    // 2: Temp
                    // 3: Rain (Precip)
                    // 5: Wind (Vento)
                    // 9: Humidity (UR%)
                    
                    if (tds.size >= 10) {
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
                        
                        // Dynamic column parsing based on table headers usually, but here we can try heuristics or fixed offsets if constant.
                        // Standard: 6=Quota, 7=Aria, 8=Vis, 9=UR
                        // Seaside: 5=Vento, 6=Onde, 7=Quota, 8=Tperc, 9=Vis?
                        
                        // Let's look for known markers in the row or just check size.
                        // However, headers are safer. But inside loop we only have tds.
                        // We can scan headers once per table? 
                        // Simplified approach: Check if we have "T perc" in the 8th column (index 7) or 9th?
                        // Actually, let's just grab "UR" if present, else "T perc".
                        
                        var lastColValue = "--"
                        var lastColLabel = "UR%"
                        
                        // Layout Logic:
                        // Standard (Torino):
                        // 0: Ora, 1: Icon, 2: Temp, 3: Prec, 4: WindIcon, 5: WindParams, 
                        // 6: Quota (has "m" or "neve"), 7: Aria, 8: Vis, 9: UR (value only)
                        
                        // Seaside (Palermo):
                        // 0: Ora, ... 5: WindParams, 
                        // 6: Onde (numeric), 7: Quota (has "m"), 8: Tperc, 9: Vis, ...
                        
                        val col6 = if (tds.size > 6) tds[6].text().trim() else ""
                        val col7 = if (tds.size > 7) tds[7].text().trim() else ""
                        
                        // Determine layout
                        val isSeaside = if (col6.all { it.isDigit() } && (col7.contains("m") || col7.contains("neve") || col7.contains("Quota"))) {
                            true
                        } else {
                            false
                        }
                        
                        if (isSeaside) {
                            // Seaside: Tperc is at index 8
                             if (tds.size > 8) {
                                 lastColValue = tds[8].text()
                                 if (!lastColValue.contains("°")) lastColValue += "°"
                                 lastColLabel = "TP°"
                             }
                        } else {
                            // Standard: UR is at index 9
                            // Note: previous inspection showed 14 cols, index 9 was "93"
                            if (tds.size > 9) {
                                lastColValue = tds[9].text()
                                if (!lastColValue.contains("%")) lastColValue += "%"
                                lastColLabel = "UR%"
                            }
                        }
                        
                        // Standard Columns mapping (best effort)
                        // Snow/Quota is col 6 in Standard, col 7 in Seaside
                        val snowLevel = if (isSeaside) col7 else col6
                        
                        // Visibility
                        // Standard: col 8 (<10km)
                        // Seaside: col 9 (>10km)
                        val visibilityIndex = if (isSeaside) 9 else 8
                        val visibility = if (tds.size > visibilityIndex) tds[visibilityIndex].text() else "--"
                        
                        val airQuality = if (!isSeaside && tds.size > 7) tds[7].text() else "--"

                        hourlyItems.add(HourlyItem(time, iconUrl, weatherCode, temp, rain, rainType, wind, snowLevel, airQuality, visibility, lastColValue, lastColLabel))
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
