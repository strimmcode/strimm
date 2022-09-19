// dllmain.cpp : Defines the entry point for the DLL application.
#include "framework.h"

using namespace Gdiplus;
using namespace std;

#include "CompoundProtocol.h"

ULONG_PTR           gdiplusToken;
GdiplusStartupInput gdiplusStartupInput;
ofstream* pOfs = NULL;
bool bDebug = true;
CompoundProtocol* CP = NULL;

BOOL APIENTRY DllMain( HMODULE hModule,
                       DWORD  ul_reason_for_call,
                       LPVOID lpReserved
                     )
{
    switch (ul_reason_for_call)
    {
    case DLL_PROCESS_ATTACH:
        //Init GDI+ 
        if (bDebug) pOfs = new ofstream("DEBUG.txt");
        GdiplusStartup(&gdiplusToken, &gdiplusStartupInput, NULL);
        CP = new CompoundProtocol();
        break;
    case DLL_THREAD_ATTACH:
        break;
    case DLL_THREAD_DETACH:
        break;
    case DLL_PROCESS_DETACH:
        GdiplusShutdown(gdiplusToken);
        if (CP) {
            delete CP;
            CP = NULL;
        }


        if (bDebug && pOfs) {
            pOfs->close();
            delete pOfs;
            pOfs = NULL;
        }


        break;
    }
    return TRUE;
}

