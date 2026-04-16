package com.example.batmaps

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.batmaps.ui.theme.BatMapsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

data class Segnalazione(
    val data: String,
    val ora: String,
    val specie: String,
    val localita: String,
    val comune: String,
    val provincia: String,
    val stato: String,
    val note: String,
    val latitude: Double,
    val longitude: Double
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
    var segnalazioniConCoordinate by remember { mutableStateOf<List<Pair<Segnalazione, GeoPoint>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        segnalazioniConCoordinate = withContext(Dispatchers.IO) { leggiDati(context) }
        isLoading = false
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        if (isLoading) CircularProgressIndicator(modifier = Modifier.padding(innerPadding))
        else OSMMapView(segnalazioniConCoordinate)
    }
}

@Composable
fun OSMMapView(punti: List<Pair<Segnalazione, GeoPoint>>) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(10.0)
            controller.setCenter(GeoPoint(45.4064, 11.8768))
        }
    }

    LaunchedEffect(punti) {
        mapView.overlays.clear()
        punti.forEach { (info, coordinata) ->
            val marker = Marker(mapView)
            marker.position = coordinata
            marker.title = info.specie
            val cleanProv = info.provincia.replace("[() ]".toRegex(), "").uppercase()
            val popup = "Località: ${info.localita.ifEmpty { "-" }}\n" +
                        "Comune: ${info.comune.ifEmpty { "-" }}\n" +
                        "Provincia: ${cleanProv.ifEmpty { "-" }}\n" +
                        "----------------\n" +
                        "Data: ${info.data} ore ${info.ora}\n" +
                        "Stato: ${info.stato}\n" +
                        (if (info.note.isNotEmpty()) "Note: ${info.note}" else "")
            marker.snippet = popup
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }
    DisposableEffect(mapView) { onDispose { mapView.onDetach() } }
    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

suspend fun leggiDati(context: Context): List<Pair<Segnalazione, GeoPoint>> {
    val lista = mutableListOf<Pair<Segnalazione, GeoPoint>>()
    try {
        val assets = context.assets.list("") ?: emptyArray()
        val excelFiles = assets.filter { it.startsWith("Pipistrelli") && it.endsWith(".xlsx") }
        
        for (fileName in excelFiles) {
            Log.d("BatMaps", "Analizzo file: $fileName")
            val inputStream = context.assets.open(fileName)
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            val formatter = DataFormatter()

            var headerRowIdx = -1
            for (i in 0..minOf(sheet.lastRowNum, 50)) {
                val row = sheet.getRow(i) ?: continue
                for (j in 0 until row.lastCellNum.toInt()) {
                    val cellVal = row.getCell(j)?.toString()?.lowercase()?.trim() ?: ""
                    if (cellVal == "latitudine" || cellVal == "lat") {
                        headerRowIdx = i
                        break
                    }
                }
                if (headerRowIdx != -1) break
            }
            if (headerRowIdx == -1) headerRowIdx = 0

            val hRow = sheet.getRow(headerRowIdx)
            val cols = mutableMapOf<String, Int>()
            for (i in 0 until hRow.lastCellNum.toInt()) {
                val v = hRow.getCell(i)?.toString()?.lowercase()?.trim() ?: ""
                when {
                    v == "latitudine" || v == "lat" -> cols["lat"] = i
                    v == "longitudine" || v == "long" || v == "lon" -> cols["lon"] = i
                    v.contains("provincia") || v == "sigla" -> cols["pr"] = i
                    v.contains("specie") -> cols["sp"] = i
                    v.contains("comune") -> cols["com"] = i
                    v.contains("localit") || v.contains("frazion") -> cols["loc"] = i
                    v.contains("data") -> cols["dt"] = i
                    v.contains("ora") -> cols["tm"] = i
                    v.contains("stato") -> cols["st"] = i
                    v.contains("note") || v.contains("condizioni") -> cols["nt"] = i
                }
            }

            for (i in (headerRowIdx + 1)..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val latStr = if (cols.containsKey("lat")) formatter.formatCellValue(row.getCell(cols["lat"]!!)).replace(',', '.') else ""
                val lonStr = if (cols.containsKey("lon")) formatter.formatCellValue(row.getCell(cols["lon"]!!)).replace(',', '.') else ""
                
                val lat = latStr.toDoubleOrNull()
                val lon = lonStr.toDoubleOrNull()

                if (lat != null && lon != null && lat != 0.0) {
                    val rawTm = if (cols.containsKey("tm")) formatter.formatCellValue(row.getCell(cols["tm"]!!)) else ""
                    val timeClean = if (rawTm.count { it == ':' } >= 2) rawTm.substringBeforeLast(":") else rawTm

                    lista.add(Segnalazione(
                        data = if (cols.containsKey("dt")) formatter.formatCellValue(row.getCell(cols["dt"]!!)) else "",
                        ora = timeClean,
                        specie = if (cols.containsKey("sp")) formatter.formatCellValue(row.getCell(cols["sp"]!!)) else "Pipistrello",
                        localita = if (cols.containsKey("loc")) formatter.formatCellValue(row.getCell(cols["loc"]!!)) else "",
                        comune = if (cols.containsKey("com")) formatter.formatCellValue(row.getCell(cols["com"]!!)) else "",
                        provincia = if (cols.containsKey("pr")) formatter.formatCellValue(row.getCell(cols["pr"]!!)) else "",
                        stato = if (cols.containsKey("st")) formatter.formatCellValue(row.getCell(cols["st"]!!)) else "",
                        note = if (cols.containsKey("nt")) formatter.formatCellValue(row.getCell(cols["nt"]!!)) else "",
                        latitude = lat, longitude = lon
                    ) to GeoPoint(lat, lon))
                }
            }
            workbook.close()
        }
    } catch (e: Exception) { Log.e("BatMaps", "Errore: ${e.message}") }
    Log.d("BatMaps", "Totale caricate: ${lista.size}")
    return lista
}
