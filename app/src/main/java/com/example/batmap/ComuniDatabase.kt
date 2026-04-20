package com.example.batmap

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
        val reg: String,
        val lat: Double,
        val lon: Double
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
                    reg = item.optString("reg", "-"),
                    lat = item.getDouble("lat"),
                    lon = item.getDouble("lon")
                )
            }
            databaseCompleto = tempMap
            isInitialized = true
            Log.d("ComuniDatabase", "Database inizializzato con ${databaseCompleto.size} comuni.")
        } catch (e: Exception) {
            Log.e("ComuniDatabase", "Errore nel caricamento JSON: ${e.message}")
        }
    }

    data class Result(
        val nome: String,
        val prov: String,
        val reg: String,
        val lat: Double,
        val lon: Double
    )

    fun cercaDati(comune: String, localita: String, provincia: String): Result {
        val p = provincia.uppercase().trim()
        var c = comune.lowercase().trim().replace('\u00A0', ' ')
        val l = localita.lowercase().trim().replace('\u00A0', ' ')

        // Normalizzazione manuale per errori comuni
        if (c == "due ville" || c == "dueville") c = "dueville"
        if (c == "camisano" || c == "camisano vicentino") c = "camisano vicentino"
        if (c == "grisignao" || c == "grisignano" || c == "grisignano di zocco") c = "grisignano di zocco"
        if (c == "montecchio" || c == "montecchio vicenza" || c == "montecchio maggiore") {
            c = "montecchio maggiore"
        }

        // 1. Priorità: Comune esatto nel DB
        databaseCompleto[c]?.let {
            return Result(c, it.prov, it.reg, it.lat, it.lon)
        }

        // 2. Priorità: Località (cerca se il testo della località contiene un nome di comune)
        // Ordiniamo per lunghezza decrescente per evitare match parziali (es. "Sesto" invece di "Sesto San Giovanni")
        val comuniOrdinati = databaseCompleto.keys.sortedByDescending { it.length }
        for (nome in comuniOrdinati) {
            if (nome.length > 3 && l.contains(nome)) {
                val info = databaseCompleto[nome]!!
                return Result(nome, info.prov, info.reg, info.lat, info.lon)
            }
        }

        // 3. Priorità: Sigla Provincia (usa coordinate capoluogo)
        if (p.length == 2) {
            val capoluogo = getCapoluogo(p)
            databaseCompleto[capoluogo]?.let {
                return Result(capoluogo, p, it.reg, it.lat, it.lon)
            }
        }

        return Result("vicenza", "VI", "Veneto", 45.5479, 11.5446)
    }

    fun getRegioni(): List<String> {
        return databaseCompleto.values.map { it.reg }.distinct().sorted()
    }

    fun getProvince(regione: String? = null): List<String> {
        return databaseCompleto.values
            .filter { regione == null || it.reg == regione }
            .map { it.prov }
            .distinct()
            .sorted()
    }

    private fun getCapoluogo(prov: String): String {
        return when(prov) {
            "AG" -> "agrigento"; "AL" -> "alessandria"; "AN" -> "ancona"; "AO" -> "aosta"; "AQ" -> "l'aquila"
            "AR" -> "arezzo"; "AP" -> "ascoli piceno"; "AT" -> "asti"; "AV" -> "avellino"; "BA" -> "bari"
            "BT" -> "barletta"; "BL" -> "belluno"; "BN" -> "benevento"; "BG" -> "bergamo"; "BI" -> "biella"
            "BO" -> "bologna"; "BZ" -> "bolzano"; "BS" -> "brescia"; "BR" -> "brindisi"; "CA" -> "cagliari"
            "CL" -> "caltanissetta"; "CB" -> "campobasso"; "CE" -> "caserta"; "CT" -> "catania"; "CZ" -> "catanzaro"
            "CH" -> "chieti"; "CO" -> "como"; "CS" -> "cosenza"; "CR" -> "cremona"; "KR" -> "crotone"
            "CN" -> "cuneo"; "EN" -> "enna"; "FM" -> "fermo"; "FE" -> "ferrara"; "FI" -> "firenze"
            "FG" -> "foggia"; "FC" -> "forli'"; "FR" -> "frosinone"; "GE" -> "genova"; "GO" -> "gorizia"
            "GR" -> "grosseto"; "IM" -> "imperia"; "IS" -> "isernia"; "SP" -> "la spezia"; "LT" -> "latina"
            "LE" -> "lecce"; "LC" -> "lecco"; "LI" -> "livorno"; "LO" -> "lodi"; "LU" -> "lucca"
            "MC" -> "macerata"; "MN" -> "mantova"; "MS" -> "massa"; "MT" -> "matera"; "ME" -> "messina"
            "MI" -> "milano"; "MO" -> "modena"; "MB" -> "monza"; "NA" -> "napoli"; "NO" -> "novara"
            "NU" -> "nuoro"; "OR" -> "oristano"; "PD" -> "padova"; "PA" -> "palermo"; "PR" -> "parma"
            "PV" -> "pavia"; "PG" -> "perugia"; "PU" -> "pesaro"; "PE" -> "pescara"; "PC" -> "piacenza"
            "PI" -> "pisa"; "PT" -> "pistoia"; "PN" -> "pordenone"; "PZ" -> "potenza"; "PO" -> "prato"
            "RG" -> "ragusa"; "RA" -> "ravenna"; "RC" -> "reggio calabria"; "RE" -> "reggio emilia"; "RI" -> "rieti"
            "RN" -> "rimini"; "RM" -> "roma"; "RO" -> "rovigo"; "SA" -> "salerno"; "SS" -> "sassari"
            "SV" -> "savona"; "SI" -> "siena"; "SR" -> "siracusa"; "SO" -> "sondrio"; "TA" -> "taranto"
            "TE" -> "teramo"; "TR" -> "terni"; "TO" -> "torino"; "TP" -> "trapani"; "TN" -> "trento"
            "TV" -> "treviso"; "TS" -> "trieste"; "UD" -> "udine"; "VA" -> "varese"; "VE" -> "venezia"
            "VB" -> "verbania"; "VC" -> "vercelli"; "VR" -> "verona"; "VV" -> "vibo valentia"; "VI" -> "vicenza"
            "VT" -> "viterbo"
            else -> "vicenza"
        }
    }
}
