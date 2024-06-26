package info.nightscout.sharedtests

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.core.extensions.pureProfileFromJson
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.embedments.InsulinConfiguration
import info.nightscout.implementation.profile.ProfileStoreObject
import info.nightscout.implementation.profile.ProfileUtilImpl
import info.nightscout.implementation.utils.DecimalFormatterImpl
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.profile.ProfileStore
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ProfileUtil
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock

@Suppress("SpellCheckingInspection")
open class TestBaseWithProfile : TestBase() {

    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var config: Config
    @Mock lateinit var context: Context
    @Mock lateinit var sp: SP

    lateinit var dateUtil: DateUtil
    lateinit var profileUtil: ProfileUtil
    lateinit var decimalFormatter: DecimalFormatter
    lateinit var hardLimits: HardLimits
    val rxBus = RxBus(aapsSchedulers, aapsLogger)

    val profileInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is ProfileStoreObject) {
                it.aapsLogger = aapsLogger
                it.activePlugin = activePlugin
                it.config = config
                it.rh = rh
                it.rxBus = rxBus
                it.hardLimits = hardLimits
            }
        }
    }

    private lateinit var validProfileJSON: String
    private lateinit var invalidProfileJSON: String
    lateinit var validProfile: ProfileSealed.Pure
    lateinit var effectiveProfileSwitch: EffectiveProfileSwitch
    lateinit var testPumpPlugin: TestPumpPlugin

    var now = 1656358822000L

    @Suppress("PropertyName") val TESTPROFILENAME = "someProfile"

    @BeforeEach
    fun prepareMock() {
        validProfileJSON = "{\"dia\":\"5\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"3\"}," +
            "{\"time\":\"2:00\",\"value\":\"3.4\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4.5\"}]," +
            "\"target_high\":[{\"time\":\"00:00\",\"value\":\"7\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
        invalidProfileJSON = "{\"dia\":\"1\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"3\"}," +
            "{\"time\":\"2:00\",\"value\":\"3.4\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4.5\"}]," +
            "\"target_high\":[{\"time\":\"00:00\",\"value\":\"7\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
        dateUtil = Mockito.spy(DateUtil(context))
        decimalFormatter = DecimalFormatterImpl(rh)
        profileUtil = ProfileUtilImpl(sp, decimalFormatter)
        testPumpPlugin = TestPumpPlugin(profileInjector)
        Mockito.`when`(dateUtil.now()).thenReturn(now)
        Mockito.`when`(activePlugin.activePump).thenReturn(testPumpPlugin)
        Mockito.`when`(sp.getString(info.nightscout.core.utils.R.string.key_units, GlucoseUnit.MGDL.asText)).thenReturn(GlucoseUnit.MGDL.asText)
        hardLimits = HardLimitsMock(sp, rh)
        validProfile = ProfileSealed.Pure(pureProfileFromJson(JSONObject(validProfileJSON), dateUtil)!!)
        effectiveProfileSwitch = EffectiveProfileSwitch(
            timestamp = dateUtil.now(),
            basalBlocks = validProfile.basalBlocks,
            isfBlocks = validProfile.isfBlocks,
            icBlocks = validProfile.icBlocks,
            targetBlocks = validProfile.targetBlocks,
            glucoseUnit = EffectiveProfileSwitch.GlucoseUnit.MMOL,
            originalProfileName = "",
            originalCustomizedName = "",
            originalTimeshift = 0,
            originalPercentage = 100,
            originalDuration = 0,
            originalEnd = 0,
            insulinConfiguration = InsulinConfiguration("", 0, 0)
        )

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Int?>(1)
            String.format(rh.gs(string), arg1)
        }.`when`(rh).gs(anyInt(), anyInt())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Double?>(1)
            String.format(rh.gs(string), arg1)
        }.`when`(rh).gs(anyInt(), anyDouble())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<String?>(1)
            String.format(rh.gs(string), arg1)
        }.`when`(rh).gs(anyInt(), anyString())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<String?>(1)
            val arg2 = invocation.getArgument<String?>(2)
            String.format(rh.gs(string), arg1, arg2)
        }.`when`(rh).gs(anyInt(), anyString(), anyString())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<String?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            String.format(rh.gs(string), arg1, arg2)
        }.`when`(rh).gs(anyInt(), anyString(), anyInt())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Double?>(1)
            val arg2 = invocation.getArgument<String?>(2)
            String.format(rh.gs(string), arg1, arg2)
        }.`when`(rh).gs(anyInt(), anyDouble(), anyString())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Double?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            String.format(rh.gs(string), arg1, arg2)
        }.`when`(rh).gs(anyInt(), anyDouble(), anyInt())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Int?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            String.format(rh.gs(string), arg1, arg2)
        }.`when`(rh).gs(anyInt(), anyInt(), anyInt())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Int?>(1)
            val arg2 = invocation.getArgument<String?>(2)
            String.format(rh.gs(string), arg1, arg2)
        }.`when`(rh).gs(anyInt(), anyInt(), anyString())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Int?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            val arg3 = invocation.getArgument<String?>(3)
            String.format(rh.gs(string), arg1, arg2, arg3)
        }.`when`(rh).gs(anyInt(), anyInt(), anyInt(), anyString())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Int?>(1)
            val arg2 = invocation.getArgument<String?>(2)
            val arg3 = invocation.getArgument<String?>(3)
            String.format(rh.gs(string), arg1, arg2, arg3)
        }.`when`(rh).gs(anyInt(), anyInt(), anyString(), anyString())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Double?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            val arg3 = invocation.getArgument<String?>(3)
            String.format(rh.gs(string), arg1, arg2, arg3)
        }.`when`(rh).gs(anyInt(), anyDouble(), anyInt(), anyString())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<String?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            val arg3 = invocation.getArgument<String?>(3)
            String.format(rh.gs(string), arg1, arg2, arg3)
        }.`when`(rh).gs(anyInt(), anyString(), anyInt(), anyString())

    }

    fun getValidProfileStore(): ProfileStore {
        val json = JSONObject()
        val store = JSONObject()
        store.put(TESTPROFILENAME, JSONObject(validProfileJSON))
        json.put("defaultProfile", TESTPROFILENAME)
        json.put("store", store)
        return ProfileStoreObject(profileInjector, json, dateUtil)
    }

    fun getInvalidProfileStore1(): ProfileStore {
        val json = JSONObject()
        val store = JSONObject()
        store.put(TESTPROFILENAME, JSONObject(invalidProfileJSON))
        json.put("defaultProfile", TESTPROFILENAME)
        json.put("store", store)
        return ProfileStoreObject(profileInjector, json, dateUtil)
    }

    fun getInvalidProfileStore2(): ProfileStore {
        val json = JSONObject()
        val store = JSONObject()
        store.put(TESTPROFILENAME, JSONObject(validProfileJSON))
        store.put("invalid", JSONObject(invalidProfileJSON))
        json.put("defaultProfile", TESTPROFILENAME + "invalid")
        json.put("store", store)
        return ProfileStoreObject(profileInjector, json, dateUtil)
    }
}
