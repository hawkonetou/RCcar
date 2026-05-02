package com.hotwheels.command.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Bypass de la coupure batterie. Volatile (pas de persistance — on ne veut pas
 * relancer l'app dans cet etat dangereux ; chaque session repart desactivee).
 *
 * Quand actif, l'app envoie une commande BYPASS_BAT:1 au firmware et debloque les
 * sliders cote UI meme si batterie en cutoff. A reserver aux situations de
 * recuperation (ramener la voiture jusqu'au chargeur).
 */
object BatteryBypassStore {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled

    fun set(value: Boolean) { _enabled.value = value }
    fun toggle() { _enabled.value = !_enabled.value }
}
