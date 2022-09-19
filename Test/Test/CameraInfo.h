/*
@file CameraInfo.h
@author Terry Wright
@email tw567@cam.ac.uk

The CameraInfo object contains information about each camera-feed, including width, height and bitdepth and binning.
One constr also contains information about the ROIs which are in the STRIMM feed.
It has a fixed and constant size and is used by the CameraMapHeader as to specify the camera feed in the message
map for the GDI+ rendering of camera feeds.

*/

#pragma once
#include <string>
#include <Windows.h>

class CameraInfo
{
public:
    CameraInfo() {
        ZeroMemory(this, sizeof(CameraInfo));
    }
    CameraInfo(char* name, int index, int width, int height, int bytesPerPixel, int binning) {
        ZeroMemory(this, sizeof(CameraInfo));
        strcpy(this->name, name);
        this->index = index;
        this->width = width;
        this->height = height;
        this->bytesPerPixel = bytesPerPixel;
        this->binning = binning; //e g 2 would mean add the next pixel

        this->imageSize = bytesPerPixel * width * height;
        this->fps = 0.0;
        this->interval = 0.0;
        structureSize = sizeof(CameraInfo);
    }
    CameraInfo(char* name, int index, int width, int height, int bytesPerPixel, int binning,
        int numRoi, int* rois_x, int* rois_y, int* rois_w, int* rois_h) {
        ZeroMemory(this, sizeof(CameraInfo));
        strcpy(this->name, name);
        this->index = index;
        this->width = width;
        this->height = height;
        this->bytesPerPixel = bytesPerPixel;
        this->binning = binning; //e g 2 would mean add the next pixel

        this->imageSize = bytesPerPixel * width * height;
        this->fps = 0.0;
        this->interval = 0.0;
        this->numRoi = numRoi;

        memcpy(this->rois_x, rois_x, numRoi * sizeof(int));
        memcpy(this->rois_y, rois_y, numRoi * sizeof(int));
        memcpy(this->rois_w, rois_w, numRoi * sizeof(int));
        memcpy(this->rois_h, rois_h, numRoi * sizeof(int));

        structureSize = sizeof(CameraInfo);
    }
    char    name[100];
    int     index;
    int     width;
    int     height;
    int     bytesPerPixel;
    int     binning;
    bool    bDirty;
 
    double  fps;
    double  interval;

    int     structureSize;
    int     imageSize;

    //each cam feed has up to 256 rectangular roi
    int     numRoi;
    int     rois_x[256];
    int     rois_y[256];
    int     rois_w[256];
    int     rois_h[256];
};


