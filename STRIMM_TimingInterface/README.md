# STRIMM Timing Interface

Rationale 
------------
STRIMM has been designed from the ground up to provide a single interface for both camera acquisitions and voltage measurements as well as to support synchronisation between multiple input channels. The initial STRIMM designs rely solely on hardware timing and data acquisition capabilities provided by a National Instruments DAQ card (e.g., the NI PCIe-6343). It was quickly decided, however, that a more suitable approach would be to provide an interface which, when implemented, would provide support for specific DAQs (similar to µManager's device adapters). Implementations of the timing interface (referred to simply as timers) are compiled to dynamic libraries (.dll on Windows and .so on Unix like operating systems) which are placed in the DAQs folder. When run, STRIMM will search the DAQs directory for libraries containing an implementation of the Timing Interface which can then be loaded and used as required.

Structure
---------
The STRIMM Timing Interface module has been implemented in C++ and comes with a Java Virtual Machine wrapper written in Kotlin (allowing it to be utilised from any JVM based language). One of STRIMM's design principals was to utilise modern languages and tool-sets whenever possible and as a result all C++ components have been written to make use of the most modern compilers and C++ standard available (The MSVC 2017 tool-chains with C++ 17 features enabled at the time of writing). The Timing Interface was designed to allow flexibility in the choice of development tools for third party developers wanting to create Timer implementations and as a result any compiler capable of implementing the interface and producing a valid dynamic library can be used. The timing interface is split into four modules:

1. Timing Interface
2. Timer Implementations
3. STRIMM C++ Runtime
4. STRIMM C++ Runtime Wrapper

#### Timing Interface module
The Timing Interface module provides the interface which needs to be implemented to provide support for the specific DAQ type and the implementations of the majority of the function that are exported from the resulting dynamic libraries. It is compiled to a static library which is linked against by both the STRIMM C++ Runtime and implementations of the interface. The interface exported from the dynamic libraries is comprised of C99 style functions with C linkage. This was done to provide support for different tool-chains and compilers by avoiding issues to with inconsistent object memory layouts between different compilers. While the exported functions are restricted to only returning C types (and pointers to C/C++ objects), implementations of the Timing Interface are only required to implement the virtual methods of the \ref STRIMM::AbstractTimer "AbstractTimer" class. As a result implementations are free to rely on the full range of C++ features and STL classes provided by their compiler. A description of how to modify the dynamically exported functions is available [here](STRIMM_TimingInterface/NewDynamicFunction.md) but it should be noted that changing the this interface will cause incompatibilities with older Timer Implementations and therefore should be avoided unless absolutely necessary. Finally the Timing Interface contains the \ref STRIMM::PropertyCollection "PropertyCollection" class to manage properties in a similar way to µManager.


#### Timer Implementations
As mentioned in the previous section, the Timing Interface module provides the \ref STRIMM::AbstractTimer "AbstractTimer" abstract class. To create an implementation of the Timing Interface all that is required is to create a class that inherits from \ref STRIMM::AbstractTimer "AbstractTimer" and implements all its virtual methods. Additionally timer specific implementations of `GetTimerName`, `CreateTimer` and `DeleteTimer` must be provided. Once these have been implemented the project should be compiled to a dynamic library. A full explanation of how to implement a timer on Windows is given [here](NewTimer.md).

#### STRIMM C++ Runtime
The STRIMM C++ Runtime is responsible for detecting and loading timers from their dynamic libraries. It also wraps the C interface exported by the dynamic libraries to expose an object oriented interface similar to that exposed to timer implementations. It also wraps the exposed property interface in a type safe object oriented interface to remove the requirement for error checking present in µManager's string serialisation based interface. This component compiles to a static library that should be linked against by any programs wishing to make use of STRIMM timers.

#### STRIMM C++ Runtime JVM Wrapper
To allow access to the STRIMM C++ Runtime from JVM languages (and by extension the main STRIMM application) a Java Native Interface wrapper was written. The wrapper provides a slightly higher level interface than the standard C++ Runtime, abstracting away the majority of the callback specifications and native memory management, providing a more idiomatic Kotlin interface.

Projects
--------

#### STRIMMTimingInterface
Contains the Timing Interface specification and the exported dynamic library functions.

#### STRIMMNIDAQ
Implementation of the Timing Interface for use with National Instrument DAQ boards.

#### STRIMMCRuntime
Handles the loading of dynamic functions from .dll or .so files and wraps the functions in an object oriented interface, mimicking the interface on the other side of the dynamic library boundary.

#### STRIMMKotlinWrap
JVM wrapper for the STRIMM C++ Runtime

#### NIDAQTest
Project demonstrating the use of the STRIMM C++ Runtime interface with a NIDAQ board