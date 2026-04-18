package com.example.batmaps

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
    val stato: String,
    val note: String,
    val latitude: Double,
    val longitude: Double
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = "BatMapsApp/4.0"
        enableEdgeToEdge()
        setContent { BatMapsTheme { BatMapScreen() } }
    }
}

// Interrogazione ESCLUSIVA a Nominatim
suspend fun getCoordinatesFromNominatim(address: String, city: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    return@withContext try {
        // Pulizia query: se l'indirizzo contiene già il comune, non lo duplichiamo
        val queryText = if (address.lowercase().contains(city.lowercase())) address else "$address, $city"
        val query = URLEncoder.encode(queryText, "UTF-8")
        val url = URL("https://nominatim.openstreetmap.org/search?q=$query&format=json&limit=1")
        
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "BatMapsApp/4.0")
        conn.connectTimeout = 5000
        
        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(response)
        if (jsonArray.length() > 0) {
            val first = jsonArray.getJSONObject(0)
            Pair(first.getDouble("lat"), first.getDouble("lon"))
        } else null
    } catch (e: Exception) {
        null
    }
}

@Composable
fun BatMapScreen() {
    val context = LocalContext.current
    var tutteLeSegnalazioni by remember { mutableStateOf<List<Pair<Segnalazione, GeoPoint>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("Inizializzazione...") }
    
    LaunchedEffect(Unit) {
        ComuniDatabase.initialize(context)
        statusMessage = "Connessione a Nominatim..."
        tutteLeSegnalazioni = leggiExcel(context) { msg -> statusMessage = msg }
        isLoading = false
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (isLoading) {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(statusMessage, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                OSMMapView(tutteLeSegnalazioni)

                // Tag Versione in alto a destra (come richiesto)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    color = Color.White.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF2c3e50))
                ) {
                    Text(
                        text = "BatMaps 2025 - v18.00",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF2c3e50),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
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
            controller.setZoom(6.0)
            controller.setCenter(GeoPoint(41.9, 12.5))
        }
    }

    LaunchedEffect(punti) {
        mapView.overlays.clear()
        punti.forEach { (info, coordinata) ->
            val marker = Marker(mapView)
            marker.position = coordinata
            marker.title = info.specie
            val color = when {
                info.stato.lowercase().contains("liberato") -> android.graphics.Color.GREEN
                info.stato.lowercase().contains("morto") -> android.graphics.Color.RED
                else -> android.graphics.Color.BLUE
            }
            marker.icon.mutate().setTint(color)
            marker.snippet = "Data: ${info.data}\nLoc: ${info.localita}\nCom: ${info.comune}\nNote: ${info.note}"
            mapView.overlays.add(marker)
        }
        if (punti.isNotEmpty()) mapView.controller.animateTo(punti.last().second)
        mapView.invalidate()
    }
    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

suspend fun leggiExcel(context: Context, onProgress: (String) -> Unit): List<Pair<Segnalazione, GeoPoint>> = withContext(Dispatchers.IO) {
    val lista = mutableListOf<Pair<Segnalazione, GeoPoint>>()
    val formatter = DataFormatter()
    try {
        val inputStream: InputStream = context.assets.open("Pipistrelli 2025.xlsx")
        val workbook = XSSFWorkbook(inputStream)
        val sheet = workbook.getSheetAt(0)
        
        var headerIdx = -1
        for (i in 0..10) {
            val r = sheet.getRow(i) ?: continue
            if (formatter.formatCellValue(r.getCell(0)).lowercase().contains("specie")) { headerIdx = i; break }
        }
        if (headerIdx == -1) return@withContext emptyList()

        val headerRow = sheet.getRow(headerIdx)
        val colMap = mutableMapOf<String, Int>()
        for (j in 0 until headerRow.lastCellNum.toInt()) {
            val name = formatter.formatCellValue(headerRow.getCell(j)).lowercase()
            if (name.contains("specie")) colMap["specie"] = j
            if (name.contains("data")) colMap["data"] = j
            if (name.contains("localit")) colMap["loc"] = j
            if (name.contains("comune")) colMap["comune"] = j
            if (name.contains("stato")) colMap["stato"] = j
            if (name.contains("note") || name.contains("condizioni")) colMap["note"] = j
        }

        val rows = (headerIdx + 1)..sheet.lastRowNum
        for (i in rows) {
            val row = sheet.getRow(i) ?: continue
            val locRaw = formatter.formatCellValue(row.getCell(colMap["loc"] ?: -1)).trim()
            val comRaw = formatter.formatCellValue(row.getCell(colMap["comune"] ?: -1)).trim()
            if (locRaw.isBlank() && comRaw.isBlank()) continue

            // Normalizzazione nomi
            val res = ComuniDatabase.cercaDati(comRaw, locRaw, "")
            val comuneDisplay = res.nome.replaceFirstChar { it.uppercase() }
            var localitaDisplay = locRaw.replace('\u00A0', ' ').trim()
            
            // Pulizia località
            if (localitaDisplay.lowercase().contains(res.nome.lowercase()) && localitaDisplay.lowercase() != res.nome.lowercase()) {
                localitaDisplay = localitaDisplay.replace(Regex("[,\\s]*${Regex.escape(res.nome)}[,\\s]*", RegexOption.IGNORE_CASE), " ").trim()
            }
            if (localitaDisplay.isBlank()) localitaDisplay = comuneDisplay

            onProgress("Geocodifica: $localitaDisplay...")

            // UNICA FONTE COORDINATE: NOMINATIM
            val coords = getCoordinatesFromNominatim(localitaDisplay, comuneDisplay)
            
            if (coords != null) {
                val data = formatter.formatCellValue(row.getCell(colMap["data"] ?: -1))
                val specie = formatter.formatCellValue(row.getCell(colMap["specie"] ?: -1))
                val stato = formatter.formatCellValue(row.getCell(colMap["stato"] ?: -1))
                val note = formatter.formatCellValue(row.getCell(colMap["note"] ?: -1))
                
                lista.add(Segnalazione(data, specie, localitaDisplay, comuneDisplay, stato, note, coords.first, coords.second) to GeoPoint(coords.first, coords.second))
                
                // Delay per rispettare i termini d'uso di Nominatim (max 1 req/sec consigliata)
                delay(800) 
            }
        }
        workbook.close()
    } catch (e: Exception) { Log.e("BatMaps", "Errore: ${e.message}") }
    return@withContext lista
}
