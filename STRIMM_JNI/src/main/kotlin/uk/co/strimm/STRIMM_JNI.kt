
package uk.co.strimm

//import jdk.nashorn.internal.objects.NativeArray.forEach
import java.io.FileOutputStream
import java.io.PrintWriter
import java.lang.Math.sin
import java.util.*

data class TimerName internal constructor(val name : String)

class STRIMM_JNI(libraryLocation : String = "STRIMM_KotlinWrap") {
    var valid = false
        private set

    init {
        try {
            System.load(System.mapLibraryName(libraryLocation))
            valid = true
        } catch (e : UnsatisfiedLinkError) {
            println(e)
        }

    }

    external fun initialise() : TimerResult
    external fun deinitialise() : TimerResult

    val installedDevices
        get() = kotlin.run {
            val x = getInstalledDevicesEXT()
            x?.map(::TimerName)
        }

    private external fun getInstalledDevicesEXT() : Array<String>?

    external fun addInstallDirectory(dir : String)

    fun createTimer(deviceName: TimerName) = createTimer(deviceName.name)
    private external fun createTimer(deviceName : String) : Timer?

    external fun deleteTimer(timer : Timer)
    external fun getLastError() : String

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val tmp = (System.getProperty("user.dir") + "/DAQs/STRIMM_KotlinWrap").apply(::println)

            val runtime = STRIMM_JNI(tmp)
            if (!runtime.valid) return

            println("\n*************** Test Init ***************")
            var res = runtime.initialise()
            if (res == TimerResult.Error)
                println(runtime.getLastError())
            else
                println("Success!")

            println("\n*************** Test Init Fail ***************")
            res = runtime.initialise()
            if (res == TimerResult.Error)
                println("Success: ${runtime.getLastError()}")
            else
                println("Failed!")

            println("\n*************** Test Get Installed Devices ***************")
            runtime.addInstallDirectory(System.getProperty("user.dir") + "/DAQs/")
            val devices = runtime.installedDevices
            devices?.forEach(::println)

            if (devices != null)
            {
                println("\n*************** Test Create Timer ***************")
                val timer = runtime.createTimer(devices[0])
                /*if(runtime.loadTimer(devices[0], "tmr") == Result.Error)
                    println(runtime.getLastError())
                else
                    println("Success!")

                println("\n*************** Test Get Timer Pointer ***************")
                val timer = runtime.getDevicePointer("tmr")*/
                if (timer == null)
                    println("Failed! GetDevicePointer returned null!")
                else
                    println("Success!")


                println("\n*************** Test Get Timer Name ***************")
                timer?.getName()?.run(::println)

                println("\n*************** Test Get Property Names ***************")
                val propNames = timer?.getPropertyNames()?.apply { forEach(::println) }

                println("\n*************** Test Get Property ***************")
                val properties = propNames?.
                        map { Pair(timer.getProperty(it), it) }?.
                        apply {
                            forEach { (prop, propName) ->
                                if (prop != null)
                                    println("$propName not null!")
                                else
                                    println("$propName null!")
                            }
                        }

                println("\n*************** Test Get IsPreInit ***************")
                properties?.forEach { println("${it.second} : ${it.first?.isPreInit()}") }

                println("\n*************** Test Get PropType ***************")
                properties?.forEach { println("${it.second} : ${it.first?.getPropertyType().toString()}") }

                println("\n*************** Test Get Prop Value***************")
                properties?.forEach {
                    print("${it.second} : ")
                    it.first?.also {
                        when (it.getPropertyType())
                        {
                            PropertyType.IntegerPropertyType -> println((it as IntegerTimerProperty).value)
                            PropertyType.DoublePropertyType -> println((it as DoubleTimerProperty).value)
                            PropertyType.FloatPropertyType -> println((it as FloatTimerProperty).value)
                            PropertyType.BooleanPropertyType -> println((it as BooleanTimerProperty).value)
                            PropertyType.StringPropertyType -> println((it as StringTimerProperty).value)
                            PropertyType.UnknownPropertyType -> println("Unknown!")
                        }
                    }
                }

                properties?.map{ it.first }?.forEach { it?.also(timer::releaseProperty) }

                println("\n*************** Test Get Allowed Values ***************")
                val devNameProp = timer?.getProperty("Device Name") as StringTimerProperty?
                val connectedDevices = devNameProp?.getAllowedValues()?.apply { forEach(::println) }

                println("\n*************** Test Set Prop Value***************")
                devNameProp?.also{
                    it.value = "Dev1"
                    println("New Device Name Value : ${it.value}")
                }


                println("\n*************** Test Set Max and Min***************")
                (timer?.getProperty("Test Int") as IntegerTimerProperty).also { prop ->
                    prop.max = 100
                    prop.min = 10

                    println("Max : ${prop.max}")
                    println("Min : ${prop.min}")

                    listOf(101, 9, 50).forEach {i ->
                        prop.value = i
                        println("SET : $i\tGET : ${prop.value}")
                    }

                    timer.releaseProperty(prop)
                }


                println("\n*************** Select Device ***************")
                connectedDevices?.forEachIndexed { i, dev -> println("$i) $dev") }
                with(Scanner(System.`in`)) {
                    while (true) {
                        print("Select Device : ")
                        try {
                            val i = nextInt()
                            if (i in 0 until (connectedDevices?.size ?: 0))
                                if (devNameProp != null && connectedDevices != null) {
                                    devNameProp.value = connectedDevices[i]
                                    break
                                }

                            println("Input must be between 0 and ${(connectedDevices?.size ?: 1) - 1}")
                        } catch (e : InputMismatchException) {
                            println("Input must be integer")
                        }
                        nextLine()
                    }
                }

                when (timer.initialise()) {
                    TimerResult.Error -> println(timer.getLastError())
                    TimerResult.Warning -> println(timer.getLastError())
                    TimerResult.Success -> Unit
                }

                println("\n*************** Test Get Physical Channels ***************")
                listOf(ChannelType.AnalogueIn, ChannelType.AnalogueOut, ChannelType.DigitalIn, ChannelType.DigitalOut).
                        forEach {
                            println("$it Channels:")
                            timer.getChannels(it).forEach(::println)
                            println()
                        }

                PrintWriter(FileOutputStream("./DELETEME.csv", false)).use { out ->
                    println("\n*************** Test Add Analogue Input ***************")
                    // directly creating a ChannelName object is not normally possible use return value from getAvailableChannels instead
                    res = timer.addAnalogueInput(ChannelName("${devNameProp?.value}/ai0"), 1, 10.0, -10.0,
                            STRIMM_JArrayCallback(Unit)
                            { arr, _ ->
                                synchronized(out)
                                {
                                    out.print("AI 0,")
                                    out.println(Arrays.toString(arr).toString().drop(1).dropLast(1))
                                }
                            } )

                    if (res != TimerResult.Success)
                        println(timer.getLastError())
                    else
                        println("No Errors!")

                    println("\n*************** Test Add Analogue Output ***************")
                    // directly creating a ChannelName object is not normally possible use return value from getAvailableChannels instead
                    res = timer.addAnalogueOutput(ChannelName("${devNameProp?.value}/ao0"), 1,
                            DoubleArray(1000) { i -> sin(2*Math.PI*i.toDouble()/1000) }.apply
                            {
                                out.print("AO 0,")
                                out.println(Arrays.toString(this).drop(1).dropLast(1))
                            } )

                    if (res != TimerResult.Success)
                        println(timer.getLastError())
                    else
                        println("No Errors!")

                    println("\n*************** Test Add Digital Input ***************")
                    // directly creating a ChannelName object is not normally possible use return value from getAvailableChannels instead
                    res = timer.addDigitalInput(ChannelName("${devNameProp?.value}/port0/line7"), 1,
                            STRIMM_JArrayCallback(Unit)
                            { arr, _ ->
                                synchronized(out)
                                {
                                    out.print("DI 0,")
                                    out.println(Arrays.toString(arr).toString().drop(1).dropLast(1))
                                }
                            })

                    if (res != TimerResult.Success)
                        println(timer.getLastError())
                    else
                        println("No Errors!")

                    println("\n*************** Test Add Digital Output ***************")
                    // directly creating a ChannelName object is not normally possible use return value from getAvailableChannels instead
                    res = timer.addDigitalOutput(ChannelName("${devNameProp?.value}/port0/line0"), 1,
                            ByteArray(1000) { i -> if (i % 100 < 20) 1 else 0}.apply
                            {
                                out.print("DO 0,")
                                out.println(Arrays.toString(this).drop(1).dropLast(1))
                            } )

                    out.println()

                    if (res != TimerResult.Success)
                        println(timer.getLastError())
                    else
                        println("No Errors!")

                    timer.addStopListener( object : TimerStopListener
                    {
                        override fun stopEvent() = println("Stopping")
                        override fun errorEvent() = println(timer.getLastError())
                    })

                    println("\n*************** Test Prime Channels ***************")
                    res = timer.prime()

                    if (res != TimerResult.Success)
                        println(timer.getLastError())
                    else
                        println("No Errors!")

                    println("\n*************** Test Start Channels ***************")
                    res = timer.start()

                    if (res != TimerResult.Success)
                        println(timer.getLastError())
                    else
                    {
                        println("Starting Acquisition! Press enter to stop!")

                        readLine()

                        println("\n*************** Test Start Channels ***************")
                        res = timer.stop()

                        if (res != TimerResult.Success)
                            println(timer.getLastError())
                        else
                            println("Starting Acquisition! Press enter to stop!")
                    }

                }

                Thread.sleep(250)
                devNameProp?.also { timer.releaseProperty(it) }
            }


            println("\n*************** Test De-init ***************")
            res = runtime.deinitialise()
            if (res == TimerResult.Error)
                println(runtime.getLastError())
            else
                println("Success!")

            println("\n*************** Test De-init Fail ***************")
            res = runtime.deinitialise()
            if (res == TimerResult.Error)
                println("Success: ${runtime.getLastError()}")
            else
                println("Failed!")
        }
    }
}


