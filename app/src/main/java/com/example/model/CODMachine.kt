package com.example.model

data class CODMachine(
    val name: String,
    val branch: String,
    val merchantId: String,
    val mapUrl: String,
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        fun create(name: String, branch: String, merchantId: String, mapUrl: String): CODMachine {
            val branchLower = (branch.lowercase() + " " + name.lowercase())
            val base = when {
                branchLower.contains("sanniya") || branchLower.contains("industrial") || branchLower.contains("st16") || branchLower.contains("saniiya") || branchLower.contains("sanni") -> Pair(25.1950, 51.4110)
                branchLower.contains("bin omran") -> Pair(25.2854, 51.5310)
                branchLower.contains("rayyan") -> Pair(25.2910, 51.4244)
                branchLower.contains("wukair") || branchLower.contains("ezdan") -> Pair(25.1483, 51.5831)
                branchLower.contains("al saad") || branchLower.contains("saad") || branchLower.contains("shariyas food center") -> Pair(25.2862, 51.5002)
                branchLower.contains("al khor") || branchLower.contains("khor") -> Pair(25.6806, 51.5058)
                branchLower.contains("dafna") -> Pair(25.3263, 51.5282)
                branchLower.contains("wakra") -> Pair(25.1764, 51.6048)
                branchLower.contains("najma") || branchLower.contains("hamed") || branchLower.contains("al fajer") || branchLower.contains("friendly mart") -> Pair(25.2755, 51.5457)
                branchLower.contains("jaber") -> Pair(25.2882, 51.5376)
                branchLower.contains("um salal") || branchLower.contains("umsalal") || branchLower.contains("umm garn") || branchLower.contains("garn") || branchLower.contains("simaisma") -> Pair(25.4192, 51.4014)
                branchLower.contains("kharaitiyat") || branchLower.contains("kheesa") || branchLower.contains("khartiyat") || branchLower.contains("lulu al bida") -> Pair(25.3780, 51.4646)
                branchLower.contains("madeena khalifa") || branchLower.contains("madina") || branchLower.contains("kuwari") || branchLower.contains("kaabi") -> Pair(25.3182, 51.4880)
                branchLower.contains("muaither") || branchLower.contains("muraikh") -> Pair(25.2974, 51.3857)
                branchLower.contains("abu hamour") || branchLower.contains("mamoura") || branchLower.contains("khayrat") -> Pair(25.2343, 51.4828)
                branchLower.contains("airport") -> Pair(25.2568, 51.5430)
                branchLower.contains("salwa") || branchLower.contains("cmax") -> Pair(25.2635, 51.4423)
                branchLower.contains("waab") || branchLower.contains("shahbiyah") -> Pair(25.2678, 51.4678)
                branchLower.contains("salata") || branchLower.contains("urban food") -> Pair(25.2658, 51.5126)
                branchLower.contains("gharaffa") || branchLower.contains("kausar") -> Pair(25.3195, 51.4582)
                branchLower.contains("lusail") || branchLower.contains("bathool") -> Pair(25.4245, 51.5330)
                branchLower.contains("muglina") || branchLower.contains("blue diamond") || branchLower.contains("road track") -> Pair(25.2810, 51.5490)
                branchLower.contains("muntazah") || branchLower.contains("smart shopping") || branchLower.contains("galaxy food") || branchLower.contains("kfone") || branchLower.contains("better buys") -> Pair(25.2709, 51.5273)
                branchLower.contains("mansoura") || branchLower.contains("fast grocery") -> Pair(25.2743, 51.5385)
                branchLower.contains("thumama") || branchLower.contains("maherjeh") -> Pair(25.2327, 51.5542)
                branchLower.contains("midmac") || branchLower.contains("prime mart") -> Pair(25.2657, 51.4920)
                branchLower.contains("bin mahmood") || branchLower.contains("carry food") -> Pair(25.2850, 51.5111)
                branchLower.contains("doha jadeed") || branchLower.contains("grand bazar") || branchLower.contains("new madeena") -> Pair(25.2797, 51.5377)
                else -> Pair(25.2854, 51.5310) // Fallback Central Doha
            }

            // Reproducible hash-based offset coordinates (+/- ~1.2km)
            val seed = merchantId.toDoubleOrNull() ?: 0.0
            val offsetLat = (((seed * 17).toLong() % 161) - 80) / 15000.0
            val offsetLng = (((seed * 31).toLong() % 161) - 80) / 15000.0

            return CODMachine(
                name = name,
                branch = branch,
                merchantId = merchantId,
                mapUrl = mapUrl,
                latitude = base.first + offsetLat,
                longitude = base.second + offsetLng
            )
        }
    }
}

data class LocationPreset(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val description: String
)
