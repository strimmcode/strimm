# Add/Remove Dynamically Loaded Functions to/from the Timing Interface

### Note: This document is meant for developers of the Timing Interface not developers wishing to create an implementation of the Timing Interface. Changes to the Dynamic Function interface will make all current Timers **incompatible** with STRIMM.

To add a new dynamically loaded function:

1. Add the function declaration to the `extern "C"` block in dynamic_functions.h
2. Implement the function in the `extern "C"` block in dynamic_functions.cpp
3. Add its type definition to dynamic_functions.h inside the `#ifdef _STRIMM_C_RUNTIME_BUILD` block
4. Add a corresponding member to TimerLibFunctions in dynamic_functions.h
5. Add a null check to the `isValid` function in dynamic_functions.h
6. Load the function in the `getLibFuncs()` function in dynamic_functions.cpp
7. Add the appropriate call of `STRIMM_DYNAMIC_MISSING_CHECK()` to the TimerLibFunctions::outputMissing() function in dynamic_functions.cpp
8. Add the wrapper to the appropriate place in STRIMM_CRuntime
9. Increment the value of `STRIMM_C_API_VERSION` at the top of dynamic_functions.h

Removing a dynamically loaded function from the interface should be performed by incrementing `STRIMM_C_API_VERSION` and then undoing steps 8 to 1.
