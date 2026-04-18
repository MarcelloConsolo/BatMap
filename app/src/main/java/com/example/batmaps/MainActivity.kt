package com.example.batmaps

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
    val regione: String,
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
    var tutteLeSegnalazioni by remember { mutableStateOf<List<Pair<Segnalazione, GeoPoint>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Stati per i filtri
    var filtroSpecie by remember { mutableStateOf("Tutte") }
    var filtroRegione by remember { mutableStateOf("Tutte") }
    var filtroStato by remember { mutableStateOf("Tutti") }
    var showFilterDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ComuniDatabase.initialize(context)
        tutteLeSegnalazioni = withContext(Dispatchers.IO) { leggiExcel(context) }
        isLoading = false
    }

    val segnalazioniFiltrate = remember(tutteLeSegnalazioni, filtroSpecie, filtroRegione, filtroStato) {
        tutteLeSegnalazioni.filter { (s, _) ->
            (filtroSpecie == "Tutte" || s.specie == filtroSpecie) &&
            (filtroRegione == "Tutte" || s.regione == filtroRegione) &&
            (filtroStato == "Tutti" || s.stato.contains(filtroStato, ignoreCase = true))
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = { showFilterDialog = true }) {
                Text("Filtri", modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                OSMMapView(segnalazioniFiltrate)
                
                // Box per il Totale
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                        .background(Color.White.copy(alpha = 0.8f), MaterialTheme.shapes.medium).padding(8.dp)
                ) {
                    Text("Totale: ${segnalazioniFiltrate.size} / ${tutteLeSegnalazioni.size}", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }

                // Box per la Legenda
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .background(Color.White.copy(alpha = 0.8f), MaterialTheme.shapes.medium)
                        .padding(8.dp)
                ) {
                    Column {
                        Text("Legenda", style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        LegendItem(Color(39, 174, 96), "Liberato / Recuperato")
                        LegendItem(Color(192, 57, 43), "Morto")
                        LegendItem(Color(247, 255, 101), "In degenza")
                        LegendItem(Color(41, 128, 185), "Altro")
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        FilterDialog(
            tutteLeSegnalazioni.map { it.first },
            currentSpecie = filtroSpecie,
            currentRegione = filtroRegione,
            onDismiss = { showFilterDialog = false },
            onApply = { s, r ->
                filtroSpecie = s
                filtroRegione = r
                showFilterDialog = false
            }
        )
    }
}

@Composable
fun FilterDialog(
    segnalazioni: List<Segnalazione>,
    currentSpecie: String,
    currentRegione: String,
    onDismiss: () -> Unit,
    onApply: (String, String) -> Unit
) {
    var tempSpecie by remember { mutableStateOf(currentSpecie) }
    var tempRegione by remember { mutableStateOf(currentRegione) }

    val specieUniche = remember(segnalazioni) {
        listOf("Tutte") + segnalazioni.map { it.specie }.distinct().filter { it.isNotBlank() }.sorted()
    }
    val regioniUniche = remember(segnalazioni) {
        listOf("Tutte") + segnalazioni.map { it.regione }.distinct().filter { it.isNotBlank() }.sorted()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtra Segnalazioni") },
        text = {
            Column {
                Text("Specie", style = MaterialTheme.typography.labelMedium)
                FilterDropdown(specieUniche, tempSpecie) { tempSpecie = it }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Regione", style = MaterialTheme.typography.labelMedium)
                FilterDropdown(regioniUniche, tempRegione) { tempRegione = it }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(tempSpecie, tempRegione) }) {
                Text("Applica")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDropdown(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        TextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.textFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall)
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
                             "Prov: ${info.provincia} (${info.regione})\n" +
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
            } else if (oRaw.contains(":")) {
                val parti = oRaw.split(":")
                if (parti.size >= 2) {
                    oRaw = "${parti[0].padStart(2, '0')}:${parti[1].padStart(2, '0')}"
                }
            }
            
            val dataFull = if (oRaw.isNotBlank() && oRaw != "-") "Segnalazione del $dRaw alle ore $oRaw" else "Segnalazione del $dRaw"
            
            val localitaRaw = formatter.formatCellValue(row.getCell(colMap["loc"] ?: -1)).trim().replace('\u00A0', ' ')
            val comuneRaw = formatter.formatCellValue(row.getCell(colMap["comune"] ?: -1)).trim()
            val provinciaRaw = formatter.formatCellValue(row.getCell(colMap["prov"] ?: -1)).trim()

            // Ottieni dati dal database
            val res = ComuniDatabase.cercaDati(comuneRaw, localitaRaw, provinciaRaw)
            
            // 1. IL COMUNE: Standardizzato dal database
            val comuneDisplay = res.nome.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

            // 2. LA LOCALITÀ: Spostamento condizionale
            var localitaDisplay = localitaRaw.replace('\u00A0', ' ').trim()
            
            // Proviamo a rimuovere il comune dalla località solo se c'è altro testo (es. la via)
            val comuneLower = res.nome.lowercase()
            val localitaLower = localitaDisplay.lowercase()
            
            if (localitaLower.contains(comuneLower)) {
                // Rimuoviamo il comune e la punteggiatura circostante
                val regex = Regex("[,\\s]*${Regex.escape(comuneLower)}[,\\s]*", RegexOption.IGNORE_CASE)
                val pulita = localitaDisplay.replace(regex, " ").trim()
                
                // Se dopo la pulizia rimane qualcosa (es. "Via Roma 1"), usiamo la versione pulita
                // Se invece non rimane nulla, significa che la località ERA il comune: in questo caso mettiamo "-"
                localitaDisplay = if (pulita.isBlank() || pulita == localitaDisplay) {
                    if (localitaLower == comuneLower) "-" else localitaDisplay
                } else {
                    pulita
                }
            }

            // Pulizia finale
            localitaDisplay = localitaDisplay
                .replace(Regex("^[,\\s.-]+"), "")
                .replace(Regex("[,\\s.-]+$"), "")
                .trim()
                .ifBlank { "-" }
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

            var lat = getCellDouble(row, colMap["lat"] ?: -1)
            var lon = getCellDouble(row, colMap["lon"] ?: -1)

            if (lat == 0.0) {
                // Se mancano i GPS, usa i dati precisi dal database con un offset piccolissimo per non sovrapporre
                lat = res.lat + (Math.random() - 0.5) * 0.05
                lon = res.lon + (Math.random() - 0.5) * 0.05
            }
            
            val stato = formatter.formatCellValue(row.getCell(colMap["stato"] ?: -1)).ifBlank { "-" }
            val note = formatter.formatCellValue(row.getCell(colMap["note"] ?: -1)).ifBlank { "-" }

            lista.add(Segnalazione(dataFull, specie.ifBlank { "Pipistrello" }, localitaDisplay, comuneDisplay, res.prov, res.reg, stato, note, lat, lon) to GeoPoint(lat, lon))
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
