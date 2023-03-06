// dllmain.cpp : Defines the entry point for the DLL application.
#include "framework.h"

using namespace Gdiplus;

ULONG_PTR           gdiplusToken;
GdiplusStartupInput gdiplusStartupInput;

BOOL APIENTRY DllMain( HMODULE hModule,
                       DWORD  ul_reason_for_call,
                       LPVOID lpReserved
                     )
{
    switch (ul_reason_for_call)
    {
    case DLL_PROCESS_ATTACH:
        //Init GDI+     
        GdiplusStartup(&gdiplusToken, &gdiplusStartupInput, NULL);
        break;
    case DLL_THREAD_ATTACH:
        break;
    case DLL_THREAD_DETACH:
        break;
    case DLL_PROCESS_DETACH:
        GdiplusShutdown(gdiplusToken);
        break;
    }
    return TRUE;
}

