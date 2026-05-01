package com.hotwheels.command.ui.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveViewModelTest {

    private val sent = mutableListOf<Int>()
    private val sender: (Int) -> Unit = { sent += it }

    @Test
    fun `slider 56 sends -56`() {
        val vm = DriveViewModel(sender)
        vm.onSliderValueChange(56)
        assertEquals(listOf(-56), sent)
    }

    @Test
    fun `release sends zero`() {
        val vm = DriveViewModel(sender)
        vm.onSliderValueChange(80)
        sent.clear()
        vm.onSliderReleased()
        assertEquals(listOf(0), sent)
    }

    @Test
    fun `slider clamps to allowed range`() {
        val vm = DriveViewModel(sender)
        vm.onSliderValueChange(250)
        vm.onSliderValueChange(-9999)
        assertEquals(listOf(-100, 100), sent)
    }
}
