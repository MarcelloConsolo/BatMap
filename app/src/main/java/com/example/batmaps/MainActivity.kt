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
        val url = URL("https://nominatim.openstreetmap.org/search?q=$query&format=json&limit=1&email=marcello.consolo@gmail.com")
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
    
    LaunchedEffect(Unit) {
        ComuniDatabase.initialize(context)
        val years = listOf(2022, 2023, 2024, 2025)
        
        for (year in years) {
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

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // 1. LA MAPPA (Deve essere il primo elemento per stare sotto)
            OSMMapView(tutteLeSegnalazioni.toList())

            // 2. FINESTRA VERSIONE (In alto a destra)
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                color = Color.White.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF2c3e50))
            ) {
                Text(
                    text = "BatMaps 2025 - v18.40",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF2c3e50),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }

            // 3. STATO CARICAMENTO (In basso)
            if (isLoading) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp).padding(horizontal = 24.dp),
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
            marker.title = "${info.specie} (${info.anno})"
            val color = when {
                info.stato.lowercase().contains("liberato") -> android.graphics.Color.GREEN
                info.stato.lowercase().contains("morto") -> android.graphics.Color.RED
                info.stato.lowercase().contains("degenza") -> android.graphics.Color.YELLOW
                else -> android.graphics.Color.BLUE
            }
            marker.icon.mutate().setTint(color)
            marker.snippet = "Data: ${info.data}\nLoc: ${info.localita}\nCom: ${info.comune} (${info.prov})\nNote: ${info.note}"
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }
    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
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
        for (j in 0 until headerRow.lastCellNum.toInt()) {
            val name = formatter.formatCellValue(headerRow.getCell(j)).lowercase()
            if (name.contains("specie")) colMap["specie"] = j
            if (name.contains("data")) colMap["data"] = j
            if (name.contains("localit")) colMap["loc"] = j
            if (name.contains("comune")) colMap["comune"] = j
            if (name.contains("stato")) colMap["stato"] = j
            if (name.contains("provin")) colMap["prov"] = j
            if (name.contains("note") || name.contains("condizioni")) colMap["note"] = j
        }

        for (i in (headerIdx + 1)..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            
            // Lettura sicura delle celle per evitare crash con indici -1
            val locRaw = colMap["loc"]?.let { formatter.formatCellValue(row.getCell(it)) }?.trim() ?: ""
            val comRaw = colMap["comune"]?.let { formatter.formatCellValue(row.getCell(it)) }?.trim() ?: ""
            val provRaw = colMap["prov"]?.let { formatter.formatCellValue(row.getCell(it)) }?.trim() ?: ""
            
            if (comRaw.isBlank()) continue

            // Query super precisa per evitare errori (Mirano -> Milano)
            val queryParts = mutableListOf<String>()
            if (locRaw.isNotBlank() && locRaw.lowercase() != comRaw.lowercase()) queryParts.add(locRaw)
            queryParts.add(comRaw)
            if (provRaw.isNotBlank()) queryParts.add(provRaw)
            queryParts.add("Veneto")
            queryParts.add("Italia")
            
            val query = queryParts.joinToString(", ")
            withContext(Dispatchers.Main) { onProgress("Geocodifica: $comRaw...") }

            // 1. Tentativo con Database Locale (veloce e sicuro per i comuni)
            val localResult = ComuniDatabase.cercaDati(comRaw, locRaw, provRaw)
            var coords: Pair<Double, Double>?

            // Se non c'è una via specifica, usiamo i dati certi del DB locale
            if (locRaw.isBlank() || locRaw.lowercase() == comRaw.lowercase()) {
                coords = Pair(localResult.lat, localResult.lon)
            } else {
                // 2. Tentativo con Nominatim per indirizzo specifico
                val nominatimCoords = getCoordinatesFromNominatim(query)
                
                if (nominatimCoords == null) {
                    // 3. Fallback: se la via fallisce, usiamo il centro del comune dal DB
                    coords = Pair(localResult.lat, localResult.lon)
                } else {
                    coords = nominatimCoords
                    // Rispetta la policy di Nominatim solo se abbiamo fatto una richiesta web
                    delay(1200)
                }
            }
            
            if (coords != null) {
                val dataStr = colMap["data"]?.let { formatter.formatCellValue(row.getCell(it)) } ?: ""
                val specieStr = colMap["specie"]?.let { formatter.formatCellValue(row.getCell(it)) } ?: "Pipistrello"
                val statoStr = colMap["stato"]?.let { formatter.formatCellValue(row.getCell(it)) } ?: ""
                val noteStr = colMap["note"]?.let { formatter.formatCellValue(row.getCell(it)) } ?: ""

                val point = Pair(
                    Segnalazione(
                        dataStr, specieStr, locRaw, comRaw, provRaw, statoStr, noteStr,
                        coords.first, coords.second, anno
                    ),
                    GeoPoint(coords.first, coords.second)
                )
                withContext(Dispatchers.Main) { onNewPoint(point) }
            }
        }
        workbook.close()
    } catch (e: Exception) { 
        Log.e("BatMaps", "Errore $fileName: ${e.message}")
    }
}
