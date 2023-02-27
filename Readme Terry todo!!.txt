1) Better naming for the dataID for batch images, currently it uses the STRIMMBuffer dataID which it received
and uses the order of the array. However there is timing info in each STRIMMBuffer
2) When use new experiment from a Moment - it does not shut down (not enough time?)
put a sleep in experimentService::65  probs want to change (did not work)
Exception: !Line 16: Property,Core,Initialize,1
Error in device "Moment": [PVCAM] ERR: pl_cam_open failed, pvErr:178, pvMsg:'This device is already open (PL_ERR_DEVICE_ALREADY_OPEN)' (20178)
even if Moment, something else Moment
however if just reload after a stop then OK