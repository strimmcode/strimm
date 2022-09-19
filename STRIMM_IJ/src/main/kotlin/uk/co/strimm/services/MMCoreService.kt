package uk.co.strimm.services

import mmcorej.*
import net.imagej.ImageJService
import org.scijava.plugin.Parameter
import org.scijava.plugin.Plugin
import org.scijava.service.Service
import org.scijava.service.AbstractService
import uk.co.strimm.MicroManager.MMDevice
import java.util.logging.Level

@Plugin(type = Service::class)
class MMCoreService : AbstractService(), ImageJService {

    @Parameter
    lateinit var log : LoggerService

    companion object {
        val core = CMMCore()

        var deviceAdapterSearchPaths
            get() = core.deviceAdapterSearchPaths.toList()
            set(value) { core.deviceAdapterSearchPaths = StrVector().apply { value.forEach(::add) } }

    }

    fun initForTests() {
        deviceAdapterSearchPaths = listOf("./DeviceAdapters")
        loadConfigurationFile("./configuration/TestConfig.cfg")
    }

    private var availableLibraries : List<MMDeviceLibrary.Library>? = null
    private var unavailableLibraries : List<MMDeviceLibrary.Unavailable>? = null

    fun getLibraries() =
            when {
                availableLibraries != null && unavailableLibraries != null ->
                    Pair(availableLibraries!!, unavailableLibraries!!)
            else -> {
                deviceAdapterSearchPaths = listOf("./DeviceAdapters")
                core.deviceAdapterNames
                        .apply { forEach { log.log(Level.INFO, "[DETECTED DEVICE]: $it") } }
                        .map { libName ->
                            try {
                                val names = core.getAvailableDevices(libName)
                                val descriptions = core.getAvailableDeviceDescriptions(libName)
                                val devTypes = core.getAvailableDeviceTypes(libName)

                                MMDeviceLibrary.Library(libName, names
                                        .zip(descriptions)
                                        .zip(LVIterator(devTypes))
                                        .map { (a, b) -> MMDeviceDesc(a.first, a.second, b) }
                                ).apply {
                                    devices.fold("Detected ${devices.size} devices:\r\n") { strOut, device ->
                                        strOut + "${DeviceType.swigToEnum(device.type)}: ${device.name}, ${device.description}\r\n"
                                    }.let { log.log(Level.INFO, it) }
                                }

                            } catch (e: Exception) {
                                log.log(Level.WARNING, "Failed to load library $libName! ${e.message}")
                                MMDeviceLibrary.Unavailable(libName)
                            }
                        }
                        .partition { it is MMDeviceLibrary.Library }
                        .let { (available, unavailable) ->
                            Pair(available.map { it as MMDeviceLibrary.Library },
                                    unavailable.map { it as MMDeviceLibrary.Unavailable })
                        }.apply { availableLibraries = first; unavailableLibraries = second }
            }
    }

    fun <T : MMDevice>getLoadedDevicesOfType(type : Class<T>) : List<T> {
        val devType = type.getDeclaredMethod("getDeviceType").invoke(null) as DeviceType

        return core.getLoadedDevicesOfType(devType).toList()
                .map { label ->
                    val deviceName = core.getDeviceName(label)
                    val deviceLibrary = core.getDeviceLibrary(label)
                    type.getConstructor(String::class.java, String::class.java, String::class.java, Boolean::class.java)
                            .newInstance(deviceName, deviceLibrary, label, true)
                }
    }

    fun loadConfigurationFile(filename: String) {
        core.loadSystemConfiguration(filename)
    }
}

class LVIterator(val lv : LongVector) : Iterable<Int> {
    override fun iterator(): Iterator<Int> = LVIterator()

    inner class LVIterator : Iterator<Int> {
        var idx = 0
        override fun hasNext(): Boolean = idx < lv.size()
        override fun next(): Int = lv[idx++]
    }
}

data class MMDeviceDesc(val name : String, val description: String, val type : Int) sealed class MMDeviceLibrary{
    data class Library(val name : String, val devices : List<MMDeviceDesc>) : MMDeviceLibrary()
    data class Unavailable(val name : String) : MMDeviceLibrary()
}





