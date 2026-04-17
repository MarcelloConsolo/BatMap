package com.example.batmaps

object ComuniDatabase {
    private val database = mapOf(
        // Capoluoghi e Province (Sigle)
        "vicenza" to "VI", "padova" to "PD", "verona" to "VR", "venezia" to "VE", "treviso" to "TV",
        "belluno" to "BL", "rovigo" to "RO", "milano" to "MI", "roma" to "RM", "torino" to "TO",
        "napoli" to "NA", "firenze" to "FI", "bologna" to "BO", "genova" to "GE", "bari" to "BA",
        "palermo" to "PA", "catania" to "CT", "messina" to "ME", "reggio calabria" to "RC",
        
        // Comuni del Vicentino (Esempio esteso)
        "arzignano" to "VI", "bassano del grappa" to "VI", "schio" to "VI", "valdagno" to "VI",
        "thiene" to "VI", "montecchio maggiore" to "VI", "lonigo" to "VI", "malo" to "VI",
        "cassola" to "VI", "rosà" to "VI", "marostica" to "VI", "chiampo" to "VI",
        "torrebelvicino" to "VI", "sandrigo" to "VI", "isola vicentina" to "VI",
        
        // Comuni del Padovano
        "albignasego" to "PD", "vigonza" to "PD", "selvazzano dentro" to "PD", "este" to "PD",
        "monselice" to "PD", "piove di sacco" to "PD", "cittadella" to "PD", "camposampiero" to "PD",
        
        // Comuni del Veronese
        "villafranca di verona" to "VR", "san giovanni lupatoto" to "VR", "san bonifacio" to "VR",
        "bussolengo" to "VR", "legnago" to "VR", "pescantina" to "VR"
        
        // Puoi aggiungere qui tutti i comuni che desideri seguendo lo schema "comune" to "PROV"
    )

    fun cercaProvincia(comune: String, localita: String): String {
        val c = comune.lowercase().trim()
        val l = localita.lowercase().trim()

        // 1. Cerca nel database dei comuni
        database[c]?.let { return it }
        
        // 2. Cerca se la sigla è scritta tra parentesi nel comune es. "Vicenza (VI)"
        val regex = Regex("\\(([a-z]{2})\\)")
        regex.find(c)?.groupValues?.get(1)?.uppercase()?.let { return it }
        regex.find(l)?.groupValues?.get(1)?.uppercase()?.let { return it }

        // 3. Cerca parole chiave nel testo (fallback)
        for ((nome, sigla) in database) {
            if (c.contains(nome) || l.contains(nome)) return sigla
        }

        return "-"
    }
}
