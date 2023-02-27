import h5py
import numpy as np
import os
import csv
from tifffile import imsave
import re


class ExtractStrimmH5:
    folder = ""
    file = ""
    image_path = []
    trace_path = []

    def __init__(self, folder, file, image_path, trace_path):
        self.folder = folder
        self.file = file
        self.image_path = image_path
        self.trace_path = trace_path
        


        

        
        h5File = h5py.File(self.folder + self.file)
        
        #process the image data
        if (self.image_path != []):
            print("saving image data")
            folder_base = self.folder + self.image_path[0]
            folder_image = self.folder + self.image_path[0] + "\\ImageData\\"             
            if not os.path.exists(folder_base):
                os.mkdir(folder_base)
            if not os.path.exists(folder_image):
                os.mkdir(folder_image)
            h1 = h5File[self.image_path[0]]
            h2 = h1[self.image_path[1]]
            h5ImageData = h2[self.image_path[2]] 
            self.image_array = []
            for i in range(0, len(h5ImageData)):
                self.image_array.append([])
            for plane in h5ImageData:
                plane_as_array = np.asarray(h5ImageData[plane])
                plane_number = int(re.findall(r'\d+', plane)[0])
                self.image_array[plane_number] = plane_as_array
                imageNameSz = folder_image +  str(plane_number) + '.tif'
                print("saving:"  + imageNameSz)
                imsave(imageNameSz, plane_as_array)  
            
        if (self.trace_path != []):
            #process the trace data
            folder_base = self.folder + self.trace_path[0]
            folder_trace = folder_base + "\\TraceData\\"
            print(folder_trace)
            if not os.path.exists(folder_base):
                os.mkdir(folder_base)
            if not os.path.exists(folder_trace):
                os.mkdir(folder_trace)
            h1 = h5File[self.trace_path[0]]
            h2 = h1[self.trace_path[1]]
            h5TraceData = h2[self.trace_path[2]]       
            dataset_name = self.trace_path[0] + "_" + self.trace_path[1] + "_" + "traceData"
            print("")
            print("")
            print("saving traceData : " + dataset_name)
        
            for plane in h5TraceData:
                plane_as_array = np.asarray(h5TraceData[plane])
                with open(folder_trace +  dataset_name + ".csv", "w", newline="") as csv_file:
                    for j in range(0, len(plane_as_array)):
                        writer = csv.writer(csv_file, delimiter=",")           
                        writer.writerow(plane_as_array[j])
            
            h5File.close()
            
        print("HDF5 to Tiff finished")




if __name__ == "__main__":
    folder = "C:\\Users\\twrig\\Desktop\\Code\\FullGraph16\\"
    file = "strimm_exp2023-01-19T07-02-19.h5"   
    image_path = ['Moment_Save' , '0' , 'imageData']
    trace_path = ['Moment_Save' , '0' , 'traceData']
    esh = ExtractStrimmH5(folder, file, image_path, trace_path)
