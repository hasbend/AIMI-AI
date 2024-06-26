package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.extension.toByteArray
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MedtrumPacketTest : MedtrumTestBase() {

    /** Test base behavior of the medtrum packet  */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is MedtrumPacket) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val opCode = 1

        // Call
        val packet = MedtrumPacket(packetInjector)
        packet.opCode = opCode.toByte()
        val result = packet.getRequest()

        // Expected values
        Assertions.assertEquals(result.size, 1)
        Assertions.assertEquals(result[0], opCode.toByte())
    }

    @Test fun handleResponseGivenResponseWhenOpcodeIsCorrectThenResultTrue() {
        // Inputs
        val opCode = 1
        val responseCode = 0
        val response = byteArrayOf(0) + opCode.toByte() + 0x0 + 0x0 + responseCode.toByteArray(2)

        // Call        
        val packet = MedtrumPacket(packetInjector)
        packet.opCode = opCode.toByte()
        val result = packet.handleResponse(response)

        // Expected values
        Assertions.assertEquals(result, true)
        Assertions.assertEquals(packet.failed, false)
    }

    @Test fun handleResponseGivenResponseWhenOpcodeIsIncorrectThenResultFalse() {
        // Inputs
        val opCode = 1
        val responseCode = 0
        val response = byteArrayOf(0) + (opCode + 1).toByte() + 0x0 + 0x0 + responseCode.toByteArray(2)

        // Call
        val packet = MedtrumPacket(packetInjector)
        packet.opCode = opCode.toByte()
        val result = packet.handleResponse(response)

        // Expected values
        Assertions.assertEquals(result, false)
        Assertions.assertEquals(packet.failed, true)
    }

    @Test fun handleResponseGivenResponseWhenResponseCodeIsWaitingThenResultFalse() {
        // Inputs
        val opCode = 1
        val responseCode = 16384
        val response = byteArrayOf(0) + opCode.toByte() + 0x0 + 0x0 + responseCode.toByteArray(2)

        // Call
        val packet = MedtrumPacket(packetInjector)
        packet.opCode = opCode.toByte()
        val result = packet.handleResponse(response)

        // Expected values
        Assertions.assertEquals(result, false)
        Assertions.assertEquals(packet.failed, false)
    }

    @Test fun handleResponseGivenResponseWhenResponseCodeIsErrorThenResultFalse() {
        // Inputs
        val opCode = 1
        val responseCode = 1
        val response = byteArrayOf(0) + opCode.toByte() + 0.toByteArray(2) + responseCode.toByteArray(2)

        // Call
        val packet = MedtrumPacket(packetInjector)
        packet.opCode = opCode.toByte()
        val result = packet.handleResponse(response)

        // Expected values
        Assertions.assertEquals(false, result)
        Assertions.assertEquals(true, packet.failed)
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val opCode = 1
        val response = byteArrayOf(0) + opCode.toByte() + 0x0 + 0x0

        // Call
        val packet = MedtrumPacket(packetInjector)
        packet.opCode = opCode.toByte()
        val result = packet.handleResponse(response)

        // Expected values
        Assertions.assertEquals(false, result)
        Assertions.assertEquals(true, packet.failed)
    }
}
