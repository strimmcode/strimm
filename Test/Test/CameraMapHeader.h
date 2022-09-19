/*
@file CameraMapHeader.h
@author Terry Wright
@email tw567@cam.ac.uk

The CameraMapHeader details the header of the image file map for the GDI+ camera feeds. Which supports multiple
camera feeds - which can be quite slow in STRIMM due to ImageJ and SciJava.
The header can accept a maximum of 10 camera feeds each with 256 ROIs potentially specified.
A key feature of the CameraMapHeader is that it contains offsets into the image file map in order to locate
the image data.
*/


#pragma once
#include "CameraInfo.h"
#include "vector"
#include <string>
#include <sstream>
#include <iostream>

using namespace std;

const int MAX_CAMERAS = 10;
//a fixed size header that contains slots for 10 cameras
class CameraMapHeader
{
public:
    CameraMapHeader(vector<CameraInfo> v_cameras) {
        numCamerasUsed = 0;
        if (v_cameras.size() > MAX_CAMERAS) {
            wstringstream wss;
            wss << L"You are trying to multiview too many cameras max is " << MAX_CAMERAS;
            MessageBox(NULL, L"MULTI-VIEW ERROR", wss.str().c_str(), MB_OK);
        }
        else
        {
            for (int f = 0; f < v_cameras.size(); f++) {
                AddCameraInfo(v_cameras[f]);
            }
            FindOffsets();
        }
    }
    int     numCamerasUsed;
    CameraInfo cameras[MAX_CAMERAS];
    int     offsets[MAX_CAMERAS];
private:
    void    AddCameraInfo(CameraInfo cam) {
        memcpy(&(cameras[numCamerasUsed]), &cam, sizeof(CameraInfo));
        numCamerasUsed++;
    }
    void    FindOffsets() {
        //fills in offsets
        DWORD scan0 = sizeof(numCamerasUsed) + sizeof(offsets) + sizeof(cameras);
        DWORD scan = scan0;
        for (int f = 0; f < numCamerasUsed; f++) {
            offsets[f] = scan;
            scan += cameras[f].imageSize;
        }
    }
};

