package info.nightscout.shared.impl.logging

import info.nightscout.rx.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LImplTest {

    @Mock lateinit var sp: SP

    private lateinit var sut: LImpl

    @BeforeEach
    fun setUp() {
        sut = LImpl(sp)
    }

    @Test
    fun findByName() {
        Assertions.assertEquals(LTag.APS.name, sut.findByName("APS").name)
        Assertions.assertEquals("NONEXISTENT", sut.findByName("APS2").name)
    }

    @Test
    fun getLogElements() {
        Assertions.assertTrue(sut.getLogElements().isNotEmpty())
    }

    @Test
    fun resetToDefaults() {
        val element = sut.findByName("APS")
        element.enabled = false
        sut.resetToDefaults()
        Assertions.assertTrue(element.enabled)
    }
}