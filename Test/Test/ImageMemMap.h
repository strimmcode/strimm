/*
@file ImageMemMap.h
@author Terry Wright
@email tw567@cam.ac.uk

The ImageMemMap class manages the file map that allows GDI+ camera feeds to function.  This class 
lays down and creates a file mapping - which means that STRIMM must be run in admin mode in Windows
for this approach to work.  Then the different camera feeds will have their images written to different
locations within the file map. These locations are specified by the CameraMapHeader. Once written into the
file map - other applications will be able to read and use the data.
In this case a windows based application creates a window for each camera feed in the file map and then
uses GDI+ to render the camera feed.  The result is that the camera feed is much smoother and nicer
than STRIMM via ImageJ.

In principle the filemap could be used by any application which might want to use the images including
applications which might want to save the data etc / or perform image processing eg Matlab.
*/


#pragma once
#include <Windows.h>
#include <stdio.h>
#include <conio.h>
#include <tchar.h>
#include <iostream>
#include <sstream>
#include <string>
#include "CameraMapHeader.h"

void con(int in, int x, int y);
extern const int MAX_CAMERAS;

class ImageMemMap
{
public:
    ImageMemMap() {
        pHead = NULL;
    }
    ImageMemMap(CameraMapHeader* pHead) {
        this->pHead = pHead;
    }
    ~ImageMemMap() {
        if (pFileMapData) UnmapViewOfFile(pFileMapData);
        if (hMapFile) CloseHandle(hMapFile);
    }

    //************************TO TEST*************
    void    CreateMemoryMap() {
        //work out the size of the filemap required. This is the header size and the size of each camera image combined.
        mapSize = sizeof(CameraMapHeader);
        for (int f = 0; f < pHead->numCamerasUsed; f++) {
            mapSize += pHead->cameras[f].imageSize;
        }
        //******************DO I NEED TO USE GLOBAL\\
        //create the filemap admin priviledges required at the moment 
        hMapFile = CreateFileMappingA(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE, 0, mapSize, "Global\\CameraMemoryMap3142");
        if (hMapFile == NULL)
        {
            MessageBoxA(NULL, "MULTI-CAM ERROR", "Unable to make the filemap administrator priviledges needed.", MB_OK);
            return;
        }
        //get a pointer to the BYTES in the filemap, this will be a 2 way IPC, so that processes displaying/saving the frames
        //have access
        pFileMapData = (BYTE*)MapViewOfFile(hMapFile, FILE_MAP_ALL_ACCESS, 0, 0, mapSize);
        if (pFileMapData == NULL)
        {
            MessageBoxA(NULL, "MULTI-CAM ERROR", "Unable to make the filemap - unspecified error", MB_OK);
            CloseHandle(hMapFile);
            return;
        }
        //copy the header across, with details about each camera along with offsets to the image frames.
        ZeroMemory(pFileMapData, 0, mapSize);
        CopyMemory(pFileMapData, pHead, sizeof(CameraMapHeader));
    }

    //**************************TO TEST
    void    OpenMemoryMap() {
        //Open an existing memory map, the map needs to have already been created and the process opening it needs to have
        //admin priviledges  ***********************DOES IT NEED TO BE GLOBAL
        //
        //initially open the map to read, we don not know how many cameras are being used and the sizes of the frames
        //associated with each camera. So find that out close the image map then reopen with the correct size.
        hMapFile = OpenFileMappingA(FILE_MAP_ALL_ACCESS, FALSE, "Global\\CameraMemoryMap3142");
        if (hMapFile == NULL)
        {
            MessageBoxA(NULL, "MULTI_CAM ERROR", "Cant open the filemap, does the map exist and does this process have admin priviledges?", MB_OK);
            return;
        }
        //get a view of the pixels
        pFileMapData = (BYTE*)MapViewOfFile(hMapFile, FILE_MAP_ALL_ACCESS, 0, 0, sizeof(CameraMapHeader));
        if (pFileMapData == NULL)
        {
            MessageBoxA(NULL, "MULTI-CAM ERROR", "Unable to make the filemap - unspecified error", MB_OK);
            CloseHandle(hMapFile);
            return;
        }
        //now update the pHead
        this->pHead = (CameraMapHeader*)pFileMapData;
        mapSize = sizeof(CameraMapHeader);
        for (int f = 0; f < pHead->numCamerasUsed; f++) {
            mapSize += pHead->cameras[f].imageSize;
        }
        if (pFileMapData) UnmapViewOfFile(pFileMapData);
        if (hMapFile) CloseHandle(hMapFile);
        //now we know the location of the image pixels
        hMapFile = OpenFileMappingA(FILE_MAP_ALL_ACCESS, FALSE, "Global\\CameraMemoryMap3142");
        if (hMapFile == NULL)
        {
            MessageBoxA(NULL, "MULTI-CAM ERROR", "Unable to make the filemap - unspecified error", MB_OK);
            return;
        }
        pFileMapData = (BYTE*)MapViewOfFile(hMapFile, FILE_MAP_ALL_ACCESS, 0, 0, mapSize);
        if (pFileMapData == NULL)
        {
            MessageBoxA(NULL, "MULTI-CAM ERROR", "Unable to make the filemap - unspecified error", MB_OK);
            CloseHandle(hMapFile);
            return;
        }
        //now update the pHead
        this->pHead = (CameraMapHeader*)pFileMapData;
        //now we know the location of the image pixels
        //will now map all of the frames - and the header
    }

    //you pass an address and it knows the size of the image to copy******************************TO_TEST
    void    AddImageData(char* szCamera, double fps, double interval, int w, int h, BYTE* pData) {
        //find the index of the camera
        string szCam1 = szCamera;
        for (int ix = 0; ix < pHead->numCamerasUsed; ix++) {
            string szCam2 = pHead->cameras[ix].name;
            if (szCam1 == szCam2) {
                pHead->cameras[ix].fps = fps;
                pHead->cameras[ix].interval = interval;
                pHead->cameras[ix].width = w;
                pHead->cameras[ix].height = h;

                pHead->cameras[ix].bDirty = true;
                return AddImageData(ix, pData);
            }
        }
        stringstream ss;
        ss << "Failed to find camera: " << (char*)szCamera;
        MessageBoxA(NULL, "MULTI_CAM ERROR", ss.str().c_str(), MB_OK);
    }

    //All data is returned as a BYTE* and needs to be cast to the correct type
    BYTE* GetImageData(int ix) {
        //con(111, 20, 20);
        pHead->cameras[ix].bDirty = false;
        return (BYTE*)(pFileMapData + pHead->offsets[ix]);
    }

    int     GetNumberOfCamerasUsed() {
        return pHead->numCamerasUsed;
    }
    string  GetCameraName(int ix) {
        if (ix >= pHead->numCamerasUsed) {
            MessageBox(NULL, L"MULTI_CAM ERROR", L"Incorrect camera index", MB_OK);
            return "";
        }

        return pHead->cameras[ix].name;
    }
    int     GetCameraWidth(int ix) {
        if (ix >= pHead->numCamerasUsed) {
            MessageBox(NULL, L"MULTI_CAM ERROR", L"Incorrect camera index", MB_OK);
            return -1;
        }
        return pHead->cameras[ix].width;
    }
    int     GetCameraHeight(int ix) {
        if (ix >= pHead->numCamerasUsed) {
            MessageBox(NULL, L"MULTI_CAM ERROR", L"Incorrect camera index", MB_OK);
            return -1;
        }
        return pHead->cameras[ix].height;
    }
    int     GetCameraBytesPerPixel(int ix) {
        if (ix >= pHead->numCamerasUsed) {
            MessageBox(NULL, L"MULTI_CAM ERROR", L"Incorrect camera index", MB_OK);
            return -1;
        }
        return pHead->cameras[ix].bytesPerPixel;
    }
    bool    GetCameraIsDirty(int ix) {
        if (ix >= pHead->numCamerasUsed) {
            MessageBox(NULL, L"MULTI_CAM ERROR", L"Incorrect camera index", MB_OK);
            return false;
        }
        return pHead->cameras[ix].bDirty;
    }
    int     GetCameraBinning(int ix) {
        if (ix >= pHead->numCamerasUsed) {
            MessageBox(NULL, L"MULTI_CAM ERROR", L"Incorrect camera index", MB_OK);
            return -1;
        }
        return pHead->cameras[ix].binning;
    }
    double  GetCameraFPS(int ix) {
        if (ix >= pHead->numCamerasUsed) {
            MessageBox(NULL, L"MULTI_CAM ERROR", L"Incorrect camera index", MB_OK);
            return 0.0;
        }
        return pHead->cameras[ix].fps;

    }
    double  GetCameraInterval(int ix) {
        if (ix >= pHead->numCamerasUsed) {
            MessageBox(NULL, L"MULTI_CAM ERROR", L"Incorrect camera index", MB_OK);
            return 0.0;
        }
        return pHead->cameras[ix].interval;
    }
    const CameraInfo&    GetCamera(int ix) {
        if (ix >= pHead->numCamerasUsed) {
            MessageBox(NULL, L"MULTI_CAM ERROR", L"Incorrect camera index", MB_OK);
            //change this
        }
        return pHead->cameras[ix];
    }

private:
    BYTE* pFileMapData; //this is the bidirectional filemap which can be used for IPC
    DWORD   mapSize;
    HANDLE  hMapFile;
    CameraMapHeader* pHead;
    void    AddImageData(int ix, BYTE* imageData) {
        memcpy(pFileMapData + pHead->offsets[ix], (void*)imageData, pHead->cameras[ix].imageSize);
        memcpy(pFileMapData, pHead, sizeof(CameraMapHeader));
    }
};


