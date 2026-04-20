package com.example.batmap

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.batmap.ui.theme.BatMapTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow
import java.io.InputStream
import java.net.URL
import kotlin.math.cos
import kotlin.math.sin

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
        Configuration.getInstance().userAgentValue = "BatMapApp/1.1"
        enableEdgeToEdge()
        setContent { BatMapTheme { BatMapApp() } }
    }
}

@Composable
fun BatMapApp() {
    var isLoggedIn by remember { mutableStateOf(false) }
    
    if (!isLoggedIn) {
        LoginScreen(onLoginSuccess = { isLoggedIn = true })
    } else {
        BatMapMainScreen()
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2C3E50)), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.width(300.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Accesso a BatMap", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF2C3E50))
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nome Utente") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showError) {
                    Text("Credenziali non valide", color = Color.Red, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (username == "test" && password == "test") {
                            onLoginSuccess()
                        } else {
                            showError = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60))
                ) {
                    Text("ACCEDI", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BatMapMainScreen() {
    val context = LocalContext.current
    val tutteLeSegnalazioni = remember { mutableStateListOf<Segnalazione>() }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("Caricamento dati...") }
    var selectedYear by remember { mutableStateOf("Tutte le segnalazioni") }
    var selectedSpecie by remember { mutableStateOf("Tutte le specie") }
    var expandedYear by remember { mutableStateOf(false) }
    var expandedSpecie by remember { mutableStateOf(false) }
    
    val availableYears = listOf("Tutte le segnalazioni", "Segnalazioni 2025", "Segnalazioni 2024", "Segnalazioni 2023", "Segnalazioni 2022")
    val availableSpecies = remember(tutteLeSegnalazioni.size) {
        val list = tutteLeSegnalazioni.map { it.specie }.distinct().sorted().toMutableList()
        list.add(0, "Tutte le specie")
        list
    }

    LaunchedEffect(Unit) {
        val years = listOf(2025, 2024, 2023, 2022)
        for (year in years) {
            statusMessage = "Caricamento anno $year..."
            val result = readExcelFromAssets(context, year)
            tutteLeSegnalazioni.addAll(result)
        }
        isLoading = false
        statusMessage = "Completato: ${tutteLeSegnalazioni.size} punti"
    }

    val visualizzate = remember(selectedYear, selectedSpecie, tutteLeSegnalazioni.size) {
        var filtered = tutteLeSegnalazioni.toList()
        if (selectedYear != "Tutte le segnalazioni") {
            val y = selectedYear.filter { it.isDigit() }.toIntOrNull() ?: 0
            filtered = filtered.filter { it.anno == y }
        }
        if (selectedSpecie != "Tutte le specie") {
            filtered = filtered.filter { it.specie == selectedSpecie }
        }
        filtered
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OSMMapView(visualizzate)

        // Panel Statistiche (Top Right)
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).padding(top = 32.dp),
            color = Color.White, shape = RoundedCornerShape(4.dp), shadowElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
        ) {
            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🦇", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("BatMap", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("🦇", fontSize = 16.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF2ECC71), RoundedCornerShape(50)))
                    Text(" Sincronizzato", fontSize = 11.sp, color = Color.Gray)
                }
                
                // Dropdown Anno
                TextButton(onClick = { expandedYear = true }) {
                    Text("$selectedYear \u25BC", fontSize = 12.sp)
                }
                DropdownMenu(expanded = expandedYear, onDismissRequest = { expandedYear = false }) {
                    availableYears.forEach { y ->
                        DropdownMenuItem(text = { Text(y) }, onClick = { selectedYear = y; expandedYear = false })
                    }
                }

                // Dropdown Specie
                TextButton(onClick = { expandedSpecie = true }) {
                    Text("$selectedSpecie \u25BC", fontSize = 12.sp)
                }
                DropdownMenu(expanded = expandedSpecie, onDismissRequest = { expandedSpecie = false }) {
                    availableSpecies.forEach { s ->
                        DropdownMenuItem(text = { Text(s) }, onClick = { selectedSpecie = s; expandedSpecie = false })
                    }
                }

                Text("Segnalazioni: ${visualizzate.size}", color = Color(0xFF27AE60), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // Legenda (Bottom Left)
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).padding(bottom = 24.dp),
            color = Color.White, shape = RoundedCornerShape(4.dp), shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Legenda", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                LegendItem(Color(0xFF2ECC71), "Liberato / Madre")
                LegendItem(Color.Red, "Morto")
                LegendItem(Color.Yellow, "In degenza")
                LegendItem(Color.Blue, "Altro")
            }
        }
        
        // Crediti (Bottom Right)
        Text(
            "BatMap | © Marcello Consolo",
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 4.dp),
            fontSize = 10.sp, color = Color.DarkGray
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                Card {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(statusMessage)
                    }
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, fontSize = 10.sp)
    }
}

@Composable
fun OSMMapView(punti: List<Segnalazione>) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(7.5)
            controller.setCenter(GeoPoint(45.42, 11.94))
        }
    }

    LaunchedEffect(punti) {
        mapView.overlays.clear()
        val occupied = mutableMapOf<String, Int>()

        punti.forEach { info ->
            val marker = Marker(mapView)
            
            // Logica Spiral Jitter (Uguale a Web)
            val key = String.format("%.4f,%.4f", info.latitude, info.longitude)
            var finalLat = info.latitude
            var finalLon = info.longitude
            val count = occupied[key] ?: 0
            if (count > 0) {
                val angle = count * 0.8
                val radius = 0.00005 * count
                finalLat += cos(angle) * radius
                finalLon += sin(angle) * radius
            }
            occupied[key] = count + 1

            marker.position = GeoPoint(finalLat, finalLon)
            
            // Stile Icona
            val st = info.stato.lowercase()
            val color = when {
                st.contains("liberato") || st.contains("madre") -> android.graphics.Color.rgb(46, 204, 113) // Verde
                st.contains("morto") -> android.graphics.Color.rgb(231, 76, 60) // Rosso
                st.contains("degenza") -> android.graphics.Color.rgb(241, 196, 15) // Giallo
                else -> android.graphics.Color.rgb(52, 152, 219) // Blu (Default)
            }
            
            val icon = context.getDrawable(org.osmdroid.library.R.drawable.marker_default)?.mutate()
            icon?.setTint(color)
            marker.icon = icon

            // Popup Personalizzato (Stile Web Natura & Legno)
            marker.infoWindow = object : MarkerInfoWindow(org.osmdroid.library.R.layout.bonuspack_bubble, mapView) {
                override fun onOpen(item: Any?) {
                    val title = mView.findViewById<android.widget.TextView>(org.osmdroid.library.R.id.bubble_title)
                    val desc = mView.findViewById<android.widget.TextView>(org.osmdroid.library.R.id.bubble_description)
                    
                    // Titolo Specie (Stile Georgia-like)
                    title.text = info.specie
                    title.setTextColor(android.graphics.Color.parseColor("#5D4037"))
                    title.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
                    title.textSize = 18f
                    title.gravity = android.view.Gravity.CENTER
                    
                    // Contorno verde per il titolo (simulato tramite padding/background se possibile, o testo)
                    title.setPadding(10, 10, 10, 10)
                    
                    val content = StringBuilder()
                    content.append("Segnalazione del ${info.data}\n\n")
                    content.append("Località: ${info.localita}\n")
                    content.append("Comune: ${info.comune}\n")
                    content.append("Provincia: ${info.prov}\n")
                    content.append("Stato: ${info.stato}\n")
                    content.append("Condizioni: ${info.note}")
                    
                    desc.text = content.toString()
                    desc.setTextColor(android.graphics.Color.DKGRAY)
                    desc.textSize = 13f
                }
            }
            
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }
    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

suspend fun readExcelFromAssets(context: Context, year: Int): List<Segnalazione> = withContext(Dispatchers.IO) {
    val result = mutableListOf<Segnalazione>()
    try {
        val inputStream = context.assets.open("Pipistrelli $year.xlsx")
        val workbook = XSSFWorkbook(inputStream)
        val sheet = workbook.getSheetAt(0)
        val formatter = DataFormatter()

        var hIdx = -1
        for (i in 0..20) {
            val r = sheet.getRow(i) ?: continue
            val txt = (0 until r.lastCellNum.toInt()).joinToString { formatter.formatCellValue(r.getCell(it)).lowercase() }
            if (txt.contains("specie") || txt.contains("data")) { hIdx = i; break }
        }
        if (hIdx == -1) return@withContext emptyList()

        val headerRow = sheet.getRow(hIdx)
        val m = mutableMapOf<String, Int>()
        for (j in 0 until headerRow.lastCellNum.toInt()) {
            val cell = headerRow.getCell(j) ?: continue
            val name = formatter.formatCellValue(cell).lowercase().trim()
            if (name.contains("specie")) m["sp"] = j
            if (name.contains("data")) m["dt"] = j
            if (name == "ora") m["ora"] = j
            if (name.contains("localit") || name.contains("indirizzo") || name.contains("via")) m["lc"] = j
            if (name.contains("comune") || name.contains("citt")) m["cm"] = j
            if (name.contains("prov") || name == "pr") m["pr"] = j
            if (name == "stato" || (name.contains("stato") && !name.contains("condizioni"))) m["st"] = j
            if (name.contains("note") || name.contains("condizioni")) m["nt"] = j
            if (name.contains("lat")) m["la"] = j
            if (name.contains("lon") || name.contains("lng")) m["lo"] = j
        }

        for (i in (hIdx + 1)..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            val latStr = formatter.formatCellValue(row.getCell(m["la"] ?: -1)).replace(",", ".")
            val lonStr = formatter.formatCellValue(row.getCell(m["lo"] ?: -1)).replace(",", ".")
            val lat = latStr.toDoubleOrNull()
            val lon = lonStr.toDoubleOrNull()
            
            if (lat != null && lon != null) {
                val dataRaw = formatter.formatCellValue(row.getCell(m["dt"] ?: -1))
                val oraRaw = m["ora"]?.let { formatter.formatCellValue(row.getCell(it)) } ?: ""
                val dataFinale = if (oraRaw.isNotBlank() && oraRaw != "00:00") "$dataRaw ora $oraRaw" else dataRaw
                
                val specieRaw = formatter.formatCellValue(row.getCell(m["sp"] ?: -1)).trim()
                val specieName = if (specieRaw.lowercase() == "nyctalus leislerii") "Nyctalus leisleri" else specieRaw
                val finalSpecie = specieName.lowercase().replaceFirstChar { it.uppercase() }

                result.add(Segnalazione(
                    data = dataFinale,
                    specie = finalSpecie,
                    localita = formatter.formatCellValue(row.getCell(m["lc"] ?: -1)),
                    comune = formatter.formatCellValue(row.getCell(m["cm"] ?: -1)),
                    prov = formatter.formatCellValue(row.getCell(m["pr"] ?: -1)),
                    stato = formatter.formatCellValue(row.getCell(m["st"] ?: -1)),
                    note = formatter.formatCellValue(row.getCell(m["nt"] ?: -1)),
                    latitude = lat,
                    longitude = lon,
                    anno = year
                ))
            }
        }
        workbook.close()
    } catch (e: Exception) {
        Log.e("BatMap", "Errore lettura assets $year: ${e.message}")
    }
    result
}
