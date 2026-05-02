package com.hotwheels.command.bluetooth

/**
 * Etat batterie remonte par l'ESP32.
 *
 * Champs diagnostic (rawAdc, pinMv, vbatMv) presents seulement avec firmware v0.4+
 * qui emet la trame enrichie `BAT:cv,pct,raw,pinMv,vbatMv`. Avec firmware v0.3 (legacy
 * `BAT:cv,pct`), ils restent a null.
 *
 * `plausible` = false quand la mesure est manifestement aberrante (cv < seuil
 * physiquement possible pour une Li-ion sous charge). L'UI doit alors afficher "--"
 * plutot qu'une fausse alerte 0%.
 */
data class BatteryState(
    val centivolts: Int,
    val percent: Int,
    val rawAdc: Int? = null,
    val pinMv: Int? = null,
    val vbatMv: Int? = null,
    val tempC: Int? = null,
    val sagPenalty: Int? = null
) {
    val volts: Float get() = centivolts / 100f

    /**
     * Une Li-ion 1S vidée s'arrête de fonctionner avant 2.80 V. Toute valeur en dessous
     * (avec un robot qui roule = consomme du courant) traduit une erreur de mesure
     * (cablage flottant, mauvais GPIO, pont mal soude). Seuil place a 2.50 V pour une
     * marge.
     */
    val plausible: Boolean get() = centivolts >= MIN_PLAUSIBLE_CV

    companion object {
        const val MIN_PLAUSIBLE_CV: Int = 250  // 2.50 V
    }
}
