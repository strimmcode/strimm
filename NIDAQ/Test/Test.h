// The following ifdef block is the standard way of creating macros which make exporting
// from a DLL simpler. All files within this DLL are compiled with the TEST_EXPORTS
// symbol defined on the command line. This symbol should not be defined on any project
// that uses this DLL. This way any other project whose source files include this file see
// TEST_API functions as being imported from a DLL, whereas this DLL sees symbols
// defined with this macro as being exported.

#define TEST_API __declspec(dllexport)

//#ifdef TEST_EXPORTS
//#define TEST_API __declspec(dllexport)
//#else
//#define TEST_API __declspec(dllimport)
//#endif

// This class is exported from the dll
extern "C" TEST_API int Test(void);


