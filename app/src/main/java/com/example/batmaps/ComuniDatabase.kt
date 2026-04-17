package com.example.batmaps

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.InputStream
import java.nio.charset.Charset

object ComuniDatabase {
    // Mappa dinamica: nome_comune -> (Provincia, Lat, Lon, Residenti, Superficie)
    private var databaseCompleto: Map<String, ComuneInfo> = emptyMap()
    private var isInitialized = false

    data class ComuneInfo(
        val prov: String,
        val lat: Double,
        val lon: Double,
        val residenti: Int,
        val superficie: Double
    )

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            val inputStream: InputStream = context.assets.open("comuni_italiani.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val jsonString = String(buffer, Charset.forName("UTF-8"))
            val jsonObject = JSONObject(jsonString)
            
            val tempMap = mutableMapOf<String, ComuneInfo>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = jsonObject.getJSONObject(key)
                tempMap[key] = ComuneInfo(
                    prov = item.getString("prov"),
                    lat = item.getDouble("lat"),
                    lon = item.getDouble("lon"),
                    residenti = item.optInt("res", 0),
                    superficie = item.optDouble("sup", 0.0)
                )
            }
            databaseCompleto = tempMap
            isInitialized = true
            Log.d("ComuniDatabase", "Database inizializzato con ${databaseCompleto.size} comuni.")
        } catch (e: Exception) {
            Log.e("ComuniDatabase", "Errore nel caricamento JSON: ${e.message}")
        }
    }

    fun cercaDati(comune: String, localita: String, provincia: String): Triple<String, Double, Double> {
        val p = provincia.uppercase().trim()
        val c = comune.lowercase().trim()
        val l = localita.lowercase().trim()

        // 1. Priorità: Comune esatto nel DB
        databaseCompleto[c]?.let { 
            return Triple(it.prov, it.lat, it.lon)
        }

        // 2. Priorità: Località (cerca se il testo della località contiene un nome di comune)
        // Nota: per performance su 8000 comuni, limitiamo la ricerca o usiamo filtri se necessario
        // Ma per volumi ridotti di segnalazioni, un ciclo va bene
        for ((nome, info) in databaseCompleto) {
            if (nome.length > 3 && l.contains(nome)) {
                return Triple(info.prov, info.lat, info.lon)
            }
        }

        // 3. Priorità: Sigla Provincia (usa coordinate capoluogo)
        if (p.length == 2) {
            val capoluogo = getCapoluogo(p)
            databaseCompleto[capoluogo]?.let {
                return Triple(p, it.lat, it.lon)
            }
        }

        // Default: Vicenza
        return Triple("VI", 45.5479, 11.5446)
    }

    private fun getCapoluogo(prov: String): String {
        return when(prov) {
            "PD" -> "padova"; "VI" -> "vicenza"; "VE" -> "venezia"; "VR" -> "verona"; "TV" -> "treviso"
            "BL" -> "belluno"; "RO" -> "rovigo"; "MO" -> "modena"; "MI" -> "milano"; "RM" -> "roma"
            "TO" -> "torino"; "BS" -> "brescia"; "BO" -> "bologna"; "TN" -> "trento"
            else -> "vicenza"
        }
    }
}
