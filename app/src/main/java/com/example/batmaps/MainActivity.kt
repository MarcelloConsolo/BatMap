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
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.InputStream
import java.util.Locale

data class Segnalazione(
    val data: String,
    val specie: String,
    val localita: String,
    val comune: String,
    val provincia: String,
    val stato: String,
    val note: String,
    var latitude: Double,
    var longitude: Double
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        enableEdgeToEdge()
        setContent { BatMapsTheme { BatMapScreen() } }
    }
}

@Composable
fun BatMapScreen() {
    val context = LocalContext.current
    var segnalazioni by remember { mutableStateOf<List<Pair<Segnalazione, GeoPoint>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        ComuniDatabase.initialize(context)
        segnalazioni = withContext(Dispatchers.IO) { leggiExcel(context) }
        isLoading = false
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                OSMMapView(segnalazioni)
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                        .background(Color.White.copy(alpha = 0.8f), MaterialTheme.shapes.medium).padding(8.dp)
                ) {
                    Text("Totale: ${segnalazioni.size}", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
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
            controller.setZoom(6.0) // Zoom out iniziale per vedere l'Italia
            controller.setCenter(GeoPoint(41.9, 12.5)) // Centro Italia
        }
    }

    LaunchedEffect(punti) {
        if (punti.isEmpty()) return@LaunchedEffect
        
        mapView.overlays.clear()
        
        // Calcolo bounding box per inquadrare tutti i punti
        var minLat = 90.0; var maxLat = -90.0
        var minLon = 180.0; var maxLon = -180.0

        punti.forEach { (info, coordinata) ->
            val marker = Marker(mapView)
            marker.position = coordinata
            marker.title = info.specie
            
            // Aggiorna limiti per zoom automatico
            minLat = minOf(minLat, coordinata.latitude)
            maxLat = maxOf(maxLat, coordinata.latitude)
            minLon = minOf(minLon, coordinata.longitude)
            maxLon = maxOf(maxLon, coordinata.longitude)

            // Imposta colore in base allo stato
            val statoLower = info.stato.lowercase()
            val color = when {
                statoLower.contains("liberato") || statoLower.contains("recuperato da madre") -> android.graphics.Color.rgb(39, 174, 96) // Verde
                statoLower.contains("morto") -> android.graphics.Color.rgb(192, 57, 43) // Rosso
                statoLower.contains("degenza") -> android.graphics.Color.rgb(247, 255, 101) // Giallo #f7ff65
                else -> android.graphics.Color.rgb(41, 128, 185) // Blu
            }
            marker.icon.mutate().setTint(color)

            marker.snippet = "${info.data}\n\n" +
                             "Loc: ${info.localita}\n" +
                             "Com: ${info.comune}\n" +
                             "Prov: ${info.provincia}\n" +
                             "Stato: ${info.stato}\n" +
                             "Condizioni: ${info.note}"
            mapView.overlays.add(marker)
        }
        
        // Auto-zoom per inquadrare tutti i punti
        if (punti.isNotEmpty()) {
            mapView.zoomToBoundingBox(
                org.osmdroid.util.BoundingBox(maxLat + 0.5, maxLon + 0.5, minLat - 0.5, minLon - 0.5),
                true
            )
        }

        mapView.invalidate()
    }
    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

fun leggiExcel(context: Context): List<Pair<Segnalazione, GeoPoint>> {
    val lista = mutableListOf<Pair<Segnalazione, GeoPoint>>()
    val formatter = DataFormatter()
    try {
        val inputStream: InputStream = context.assets.open("Pipistrelli 2025.xlsx")
        val workbook = XSSFWorkbook(inputStream)
        val sheet = workbook.getSheetAt(0)
        
        var headerIdx = -1
        for (i in 0..20) {
            val r = sheet.getRow(i) ?: continue
            for (j in 0..15) {
                val valStr = formatter.formatCellValue(r.getCell(j)).lowercase()
                if (valStr.contains("specie") || valStr.contains("data")) {
                    headerIdx = i; break
                }
            }
            if (headerIdx != -1) break
        }
        
        if (headerIdx == -1) return emptyList()

        val headerRow = sheet.getRow(headerIdx)
        val colMap = mutableMapOf<String, Int>()
        for (j in 0 until headerRow.lastCellNum.toInt()) {
            val cell = headerRow.getCell(j)
            val name = formatter.formatCellValue(cell).lowercase().trim()
            when {
                name.contains("specie") -> colMap["specie"] = j
                name.contains("data") -> colMap["data"] = j
                name.contains("ora") -> colMap["ora"] = j
                name.contains("localit") -> colMap["loc"] = j
                name.contains("comune") -> colMap["comune"] = j
                name.contains("provin") -> colMap["prov"] = j
                name.contains("stato") -> colMap["stato"] = j
                name.contains("condizioni") || name.contains("note") -> colMap["note"] = j
                name.contains("latitud") -> colMap["lat"] = j
                name.contains("longitud") -> colMap["lon"] = j
            }
        }

        for (i in (headerIdx + 1)..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            val specie = formatter.formatCellValue(row.getCell(colMap["specie"] ?: -1)).trim()
            val dRaw = formatter.formatCellValue(row.getCell(colMap["data"] ?: -1)).trim()
            
            if (specie.isBlank() && dRaw.isBlank()) continue

            // Gestione Ora
            val oraCell = row.getCell(colMap["ora"] ?: -1)
            var oRaw = formatter.formatCellValue(oraCell).trim()
            val oNum = oRaw.replace(",", ".").toDoubleOrNull()
            if (oNum != null && oNum > 0 && oNum < 1) {
                val totalSeconds = (oNum * 24 * 3600).toInt()
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                oRaw = String.format(Locale.US, "%02d:%02d", hours, minutes)
            }
            
            val dataFull = if (oRaw.isNotBlank() && oRaw != "-") "Segnalazione del $dRaw alle ore $oRaw" else "Segnalazione del $dRaw"
            
            val localita = formatter.formatCellValue(row.getCell(colMap["loc"] ?: -1)).trim()
            val comuneRaw = formatter.formatCellValue(row.getCell(colMap["comune"] ?: -1)).trim()
            val provinciaRaw = formatter.formatCellValue(row.getCell(colMap["prov"] ?: -1)).trim()

            // Ottieni dati dal database basato sulla priorità: Provincia -> Comune -> Località
            val datiBase = ComuniDatabase.cercaDati(comuneRaw, localita, provinciaRaw)
            val provSigla = datiBase.first

            var lat = getCellDouble(row, colMap["lat"] ?: -1)
            var lon = getCellDouble(row, colMap["lon"] ?: -1)

            if (lat == 0.0) {
                // Se mancano i GPS, usa i dati precisi dal database con un offset piccolissimo per non sovrapporre
                lat = datiBase.second + (Math.random() - 0.5) * 0.05
                lon = datiBase.third + (Math.random() - 0.5) * 0.05
            }
            
            val stato = formatter.formatCellValue(row.getCell(colMap["stato"] ?: -1)).ifBlank { "-" }
            val note = formatter.formatCellValue(row.getCell(colMap["note"] ?: -1)).ifBlank { "-" }

            lista.add(Segnalazione(dataFull, specie.ifBlank { "Pipistrello" }, localita, comuneRaw.ifBlank { "-" }, provSigla, stato, note, lat, lon) to GeoPoint(lat, lon))
        }
        workbook.close()
    } catch (e: Exception) { Log.e("BatMaps", "Errore: ${e.message}") }
    return lista
}

fun rigaInteraContains(riga: String, vararg parole: String): Boolean {
    for (p in parole) { if (riga.contains(p.lowercase())) return true }
    return false
}

fun getCellDouble(row: Row, index: Int): Double {
    if (index < 0) return 0.0
    val cell = row.getCell(index) ?: return 0.0
    return try {
        when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> cell.stringCellValue.replace(",", ".").trim().toDoubleOrNull() ?: 0.0
            CellType.FORMULA -> row.sheet.workbook.creationHelper.createFormulaEvaluator().evaluateInCell(cell).numericCellValue
            else -> 0.0
        }
    } catch (e: Exception) { 0.0 }
}
