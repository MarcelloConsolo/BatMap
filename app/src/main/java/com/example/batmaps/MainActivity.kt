package com.example.batmaps

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.batmaps.ui.theme.BatMapsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray

data class Segnalazione(
    val data: String,
    val specie: String,
    val localita: String,
    val comune: String,
    val prov: String,
    val stato: String,
    val note: String,
    val latitude: Double,
    val longitude: Double,
    val anno: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = "BatMapsApp/18.40"
        enableEdgeToEdge()
        setContent { BatMapsTheme { BatMapScreen() } }
    }
}

suspend fun getCoordinatesFromNominatim(queryText: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    if (queryText.isBlank()) return@withContext null
    return@withContext try {
        val query = URLEncoder.encode(queryText, "UTF-8")
        val url = URL("https://nominatim.openstreetmap.org/search?q=$query&format=json&limit=1&countrycodes=it&email=marcello.consolo@gmail.com")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "BatMapsApp/18.40")
        conn.connectTimeout = 5000
        
        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(response)
        if (jsonArray.length() > 0) {
            val first = jsonArray.getJSONObject(0)
            Pair(first.getDouble("lat"), first.getDouble("lon"))
        } else null
    } catch (e: Exception) {
        Log.e("BatMaps", "Errore Nominatim per $queryText: ${e.message}")
        null
    }
}

@Composable
fun BatMapScreen() {
    val context = LocalContext.current
    val tutteLeSegnalazioni = remember { mutableStateListOf<Pair<Segnalazione, GeoPoint>>() }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("Inizializzazione...") }
    var selectedYear by remember { mutableStateOf("Tutte le segnalazioni") }
    var expanded by remember { mutableStateOf(false) }
    val availableYears = remember { mutableStateListOf<String>() }
    
    LaunchedEffect(Unit) {
        ComuniDatabase.initialize(context)
        
        // Cerca i file Excel negli assets per determinare gli anni
        val assets = context.assets.list("") ?: emptyArray()
        val yearsFound = assets.filter { it.startsWith("Pipistrelli") && it.endsWith(".xlsx") }
            .mapNotNull { it.replace("Pipistrelli ", "").replace(".xlsx", "").toIntOrNull() }
            .sortedDescending()
        
        availableYears.clear()
        availableYears.add("Tutte le segnalazioni")
        yearsFound.forEach { availableYears.add("Segnalazioni $it") }

        for (year in yearsFound.sorted()) {
            val fileName = "Pipistrelli $year.xlsx"
            statusMessage = "Caricamento $fileName..."
            try {
                leggiExcelIncrementale(context, fileName, year,
                    onProgress = { msg -> statusMessage = "[$year] $msg" },
                    onNewPoint = { tutteLeSegnalazioni.add(it) }
                )
            } catch (e: Exception) {
                Log.e("BatMaps", "Errore file $fileName: ${e.message}")
            }
        }
        isLoading = false
        statusMessage = "Caricamento completato (${tutteLeSegnalazioni.size} punti)"
    }

    val visualizzate = remember(selectedYear, tutteLeSegnalazioni.size) {
        if (selectedYear == "Tutte le segnalazioni") {
            tutteLeSegnalazioni.toList()
        } else {
            val yearInt = selectedYear.replace("Segnalazioni ", "").toIntOrNull() ?: 0
            tutteLeSegnalazioni.filter { it.first.anno == yearInt }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // 1. LA MAPPA
            OSMMapView(visualizzate)

            // 2. FINESTRA STATISTICHE E FILTRO (Top Right)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                color = Color.White,
                shape = MaterialTheme.shapes.small,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "BatMaps 2025",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    
                    // MENU A TENDINA PER ANNO
                    Box {
                        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(0.dp)) {
                            Text(text = "$selectedYear ▼", style = MaterialTheme.typography.bodyMedium)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            availableYears.forEach { yearStr ->
                                DropdownMenuItem(
                                    text = { Text(yearStr) },
                                    onClick = {
                                        selectedYear = yearStr
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(10.dp), color = Color(0xFF2ecc71), shape = MaterialTheme.shapes.extraSmall) {}
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Sincronizzato", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Text(
                        text = "Visualizzate: ${visualizzate.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF27ae60),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }

            // 3. LEGENDA (Bottom Left - come da immagine)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .padding(bottom = 32.dp),
                color = Color.White,
                shape = MaterialTheme.shapes.small,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Legenda", style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LegendItem(Color(0xFF2ecc71), "Liberato / Recuperato da madre")
                    LegendItem(Color.Red, "Morto")
                    LegendItem(Color.Yellow, "In degenza")
                    LegendItem(Color.Blue, "Altro")
                }
            }

            // 4. STATO CARICAMENTO
            if (isLoading) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                        .padding(horizontal = 24.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(statusMessage, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun OSMMapView(punti: List<Pair<Segnalazione, GeoPoint>>) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(7.0)
            controller.setCenter(GeoPoint(45.4, 11.8))
        }
    }

    LaunchedEffect(punti.size) {
        mapView.overlays.clear()
        punti.forEach { (info, coordinata) ->
            val marker = Marker(mapView)
            marker.position = coordinata
            
            // TITOLO E DATA CENTRATI (Custom InfoWindow)
            marker.infoWindow = object : org.osmdroid.views.overlay.infowindow.MarkerInfoWindow(org.osmdroid.library.R.layout.bonuspack_bubble, mapView) {
                override fun onOpen(item: Any?) {
                    super.onOpen(item)
                    val title = mView.findViewById<android.widget.TextView>(org.osmdroid.library.R.id.bubble_title)
                    
                    val s = android.text.SpannableString("${info.specie}\nSegnalazione del ${info.data}")
                    s.setSpan(android.text.style.ForegroundColorSpan(android.graphics.Color.BLUE), 0, info.specie.length, 0)
                    s.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, info.specie.length, 0)
                    s.setSpan(android.text.style.ForegroundColorSpan(android.graphics.Color.GRAY), info.specie.length + 1, s.length, 0)
                    s.setSpan(android.text.style.RelativeSizeSpan(0.8f), info.specie.length + 1, s.length, 0)
                    
                    title.text = s
                    title.gravity = android.view.Gravity.CENTER
                    title.setPadding(0, 15, 0, 15) // Centratura verticale tra bordo e inizio descrizione
                }
            }
            
            val color = when {
                info.stato.lowercase().let { it.contains("liberato") || it.contains("madre") } -> android.graphics.Color.GREEN
                info.stato.lowercase().contains("morto") -> android.graphics.Color.RED
                info.stato.lowercase().contains("degenza") -> android.graphics.Color.YELLOW
                else -> android.graphics.Color.BLUE
            }
            marker.icon.mutate().setTint(color)
            
            // Ordine popup richiesto: Località, Comune, Provincia (Data spostata nel titolo)
            marker.snippet = "Località: ${info.localita}\n" +
                           "Comune: ${info.comune}\n" +
                           "Provincia: ${info.prov}\n" +
                           "Stato: ${info.stato}\n" +
                           "Condizioni: ${info.note}"
            
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }
    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

@Composable
fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Surface(modifier = Modifier.size(8.dp), color = color, shape = androidx.compose.foundation.shape.CircleShape) {}
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

suspend fun leggiExcelIncrementale(
    context: Context, 
    fileName: String,
    anno: Int,
    onProgress: (String) -> Unit, 
    onNewPoint: (Pair<Segnalazione, GeoPoint>) -> Unit
) = withContext(Dispatchers.IO) {
    val formatter = DataFormatter()
    try {
        val inputStream: InputStream = context.assets.open(fileName)
        val workbook = XSSFWorkbook(inputStream)
        val sheet = workbook.getSheetAt(0)
        
        var headerIdx = -1
        for (i in 0..25) {
            val r = sheet.getRow(i) ?: continue
            val rowText = (0 until r.lastCellNum.toInt()).joinToString { formatter.formatCellValue(r.getCell(it)).lowercase() }
            if (rowText.contains("specie") || rowText.contains("data")) { headerIdx = i; break }
        }
        
        if (headerIdx == -1) return@withContext

        val headerRow = sheet.getRow(headerIdx)
        val colMap = mutableMapOf<String, Int>()
        for (j in 0 until (headerRow?.lastCellNum?.toInt() ?: 0)) {
            val cell = headerRow?.getCell(j) ?: continue
            val name = formatter.formatCellValue(cell).lowercase().replace("\n", " ").trim()
            
            if (name.contains("specie")) colMap["specie"] = j
            if (name.contains("data")) colMap["data"] = j
            if (name == "ora") colMap["ora"] = j
            if (name.contains("localit") || name.contains("indirizzo") || name.contains("via")) colMap["loc"] = j
            if (name.contains("comune") || name.contains("citt") || name.contains("luogo")) colMap["comune"] = j
            if (name.contains("prov") || name == "pr") colMap["prov"] = j
            
            // Logica esclusiva per Stato e Condizioni
            if (name == "stato") {
                colMap["stato"] = j
            } else if (name == "condizioni" || name == "note") {
                colMap["note"] = j
            } else {
                if (name.contains("stato") && colMap["stato"] == null) colMap["stato"] = j
                if ((name.contains("condizioni") || name.contains("note")) && colMap["note"] == null) colMap["note"] = j
            }

            if (name.contains("lat")) colMap["lat"] = j
            if (name.contains("lon") || name.contains("lng")) colMap["lon"] = j
        }
        
        // Se manca la colonna comune, proviamo a usare la prima disponibile come fallback
        if (colMap["comune"] == null) colMap["comune"] = colMap["loc"] ?: 0

        for (i in (headerIdx + 1)..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            
            val locRaw = colMap["loc"]?.let { formatter.formatCellValue(row.getCell(it)) }?.trim() ?: ""
            val comRaw = colMap["comune"]?.let { formatter.formatCellValue(row.getCell(it)) }?.trim() ?: ""
            val provRaw = colMap["prov"]?.let { formatter.formatCellValue(row.getCell(it)) }?.trim() ?: ""
            
            // Se non abbiamo né comune né località, saltiamo
            if (comRaw.isBlank() && locRaw.isBlank()) continue

            // 1. TENTATIVO COORDINATE GIA' PRESENTI NEL FILE (Istantaneo)
            var coords: Pair<Double, Double>? = null
            val latRaw = colMap["lat"]?.let { formatter.formatCellValue(row.getCell(it)) } ?: ""
            val lonRaw = colMap["lon"]?.let { formatter.formatCellValue(row.getCell(it)) } ?: ""
            
            if (latRaw.isNotBlank() && lonRaw.isNotBlank()) {
                val lLat = latRaw.replace(",", ".").toDoubleOrNull()
                val lLon = lonRaw.replace(",", ".").toDoubleOrNull()
                if (lLat != null && lLon != null) {
                    coords = Pair(lLat, lLon)
                }
            }

            if (coords == null) {
                // 2. Tentativo con Database Locale (veloce e sicuro per i comuni)
                val localResult = ComuniDatabase.cercaDati(comRaw, locRaw, provRaw)

                // Se non c'è una via specifica, usiamo i dati certi del DB locale
                if (locRaw.isBlank() || locRaw.lowercase() == comRaw.lowercase()) {
                    coords = Pair(localResult.lat, localResult.lon)
                } else {
                    // 3. Tentativo con Nominatim per indirizzo specifico
                    val queryParts = mutableListOf<String>()
                    if (locRaw.isNotBlank()) queryParts.add(locRaw)
                    queryParts.add(comRaw)
                    if (provRaw.isNotBlank()) queryParts.add(provRaw)
                    queryParts.add("Italia")
                    val query = queryParts.joinToString(", ")

                    val nominatimCoords = getCoordinatesFromNominatim(query)
                    
                    if (nominatimCoords == null) {
                        // 4. Fallback: se la via fallisce (Timeout o altro), usiamo il centro del comune dal DB
                        coords = Pair(localResult.lat, localResult.lon)
                    } else {
                        coords = nominatimCoords
                        // Rispetta la policy di Nominatim: 1 secondo di attesa reale
                        delay(1500)
                    }
                }
            }
            
            val finalCoords = coords
            if (finalCoords != null) {
                // RICAVO LA PROVINCIA DAL COMUNE (Sempre, dando priorità al DB locale per evitare campi vuoti)
                val localResult = ComuniDatabase.cercaDati(comRaw, locRaw, provRaw)
                
                // Forza la provincia dal DB se quella dell'Excel è vuota o troppo corta
                val finalProv = if (localResult.prov.length >= 2) localResult.prov else {
                    if (provRaw.length >= 2) provRaw else ""
                }
                
                val dataStr = colMap["data"]?.let { idx ->
                    val cell = row.getCell(idx)
                    if (cell == null) ""
                    else if (cell.cellType == CellType.NUMERIC) {
                        try {
                            val d = DateUtil.getJavaDate(cell.numericCellValue, true)
                            java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ITALY).apply {
                                timeZone = java.util.TimeZone.getTimeZone("UTC")
                            }.format(d)
                        } catch (e: Exception) { formatter.formatCellValue(cell) }
                    } else formatter.formatCellValue(cell)
                } ?: ""

                val oraStr = colMap["ora"]?.let { idx ->
                    val cell = row.getCell(idx)
                    if (cell == null) ""
                    else if (cell.cellType == CellType.NUMERIC) {
                        try {
                            val valNum = cell.numericCellValue
                            // Se il numero è > 1, probabilmente contiene anche la data, prendiamo solo la frazione
                            val soloOra = valNum % 1.0
                            val d = DateUtil.getJavaDate(soloOra, true)
                            java.text.SimpleDateFormat("HH:mm", java.util.Locale.ITALY).apply {
                                timeZone = java.util.TimeZone.getTimeZone("UTC")
                            }.format(d)
                        } catch (e: Exception) { formatter.formatCellValue(cell) }
                    } else {
                        val v = formatter.formatCellValue(cell).trim()
                        if (v.contains(":")) {
                            val parts = v.split(":")
                            if (parts.size >= 2) "${parts[0].padStart(2, '0')}:${parts[1].padStart(2, '0')}" else v
                        } else v
                    }
                } ?: ""

                // Evitiamo di scrivere "ora 00:00" se l'ora è effettivamente mancante o se viene ripetuta
                val dataFinale = if (oraStr.isNotBlank() && oraStr != "00:00") {
                    "$dataStr ora $oraStr"
                } else {
                    dataStr
                }
                
                val specieStr = colMap["specie"]?.let { formatter.formatCellValue(row.getCell(it)) } ?: "Pipistrello"
                val statoStr = colMap["stato"]?.let { formatter.formatCellValue(row.getCell(it)) } ?: ""
                val noteStr = colMap["note"]?.let { formatter.formatCellValue(row.getCell(it)) } ?: ""

                val point = Pair(
                    Segnalazione(
                        dataFinale, specieStr, locRaw, comRaw, finalProv, statoStr, noteStr,
                        finalCoords.first, finalCoords.second, anno
                    ),
                    GeoPoint(finalCoords.first, finalCoords.second)
                )
                withContext(Dispatchers.Main) { onNewPoint(point) }
            }
        }
        workbook.close()
    } catch (e: Exception) { 
        Log.e("BatMaps", "Errore $fileName: ${e.message}")
    }
}
