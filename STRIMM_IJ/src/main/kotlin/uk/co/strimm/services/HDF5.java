package uk.co.strimm.services;


import ncsa.hdf.hdf5lib.*;
import ncsa.hdf.hdf5lib.exceptions.*;


public class HDF5 {
    public static void main(String []argv)
    {
        final String FILE = "dset.h5";
        int file_id = -1;       // file identifier
        int dataset_id = -1;    // dataset identifier
        int status = -1;
        int[][] dset_data = new int[4][6];

        // Initialize the dataset.
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 6; j++)
                dset_data[i][j] = 2;

        // Open an existing file
        file_id = H5Fopen_wrap (FILE, HDF5Constants.H5F_ACC_RDWR,
                HDF5Constants.H5P_DEFAULT);

        // Open an existing dataset.
        dataset_id = H5Dopen_wrap (file_id, "/dset");

        // Write the dataset.
 //       status = H5Dwrite_wrap
//                (dataset_id, HDF5Constants.H5T_NATIVE_INT,
//                        HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL,
//                        HDF5Constants.H5P_DEFAULT, dset_data);

        status = H5Dread_wrap
                (dataset_id, HDF5Constants.H5T_NATIVE_INT,
                        HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL,
                        HDF5Constants.H5P_DEFAULT, dset_data);



        // Close the dataset.
        status = H5Dclose_wrap (dataset_id);

        // Close the file.
        status = H5Fclose_wrap (file_id);

    }

    // Help function for opening an existing file
    public static int H5Fopen_wrap (String name, int flags, int access_id)
    {
        int file_id = -1;    // file identifier
        try
        {
            // Create a new file using default file properties.
            file_id = H5.H5Fopen (name, flags, access_id);
        }
        catch (HDF5Exception hdf5e)
        {
            System.out.println
                    ("DatasetRdWt.H5Fopen_wrap() with HDF5Exception: "
                            + hdf5e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println
                    ("DatasetRdWt.H5Fopen_wrap() with other Exception: "
                            + e.getMessage());
        }
        return file_id;
    }


    // Help function for opening an existing dataset
    public static int H5Dopen_wrap (int loc_id, String name)
    {
        int dataset_id = -1;  // dataset identifier

        try
        {
            // Opening an existing dataset
            dataset_id = H5.H5Dopen (loc_id, name);
        }
        catch (HDF5Exception hdf5e)
        {
            System.out.println
                    ("DatasetRdWt.H5Dopen_wrap() with HDF5Exception: "
                            + hdf5e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println
                    ("DatasetRdWt.H5Dopen_wrap() with other Exception: "
                            + e.getMessage());
        }
        return dataset_id;
    }

    // Help function for creating a new file
    public static int H5Fcreate_wrap (String name, int flags,
                                      int create_id, int access_id)
    {
        int file_id = -1;    // file identifier
        try
        {
            // Create a new file using default file properties.
            file_id = H5.H5Fcreate (name, flags, create_id, access_id);
        }
        catch (HDF5Exception hdf5e)
        {
            System.out.println
                    ("CreateFile.H5Fcreate_wrap() with HDF5Exception: "
                            + hdf5e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println
                    ("CreateFile.H5Fcreate_wrap() with other Exception: "
                            + e.getMessage());
        }

        System.out.println ("\nThe file name is: " + name);
        System.out.println ("The file ID is: " + file_id);

        return file_id;
    }


    // Help function for terminating access to the file.
    public static int H5Fclose_wrap (int file_id)
    {
        int status = -1;

        try
        {
            // Terminate access to the file.
            status = H5.H5Fclose (file_id);
        }
        catch (HDF5Exception hdf5e)
        {
            System.out.println
                    ("CreateFile.H5Fclose_wrap() with HDF5Exception: "
                            + hdf5e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println
                    ("CreateFile.H5Fclose_wrap() with other exception: "
                            + e.getMessage());
        }
        return status;
    }




    // Help function for creating a new simple dataspace and opening it
    // for access
    public static int H5Screate_simple_wrap (int rank, long dims[],
                                             long maxdims[])
    {
        int dataspace_id = -1;  // dataspace identifier

        try
        {
            // Create the data space for the dataset.
            dataspace_id = H5.H5Screate_simple (rank, dims, maxdims);
        }
        catch (HDF5Exception hdf5e)
        {
            System.out.println
                    ("CreateDataset.H5Screate_simple_wrap() with HDF5Exception: "
                            + hdf5e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println
                    ("CreateDataset.H5Screate_simple_wrap() with other Exception: "
                            + e.getMessage());
        }
        return dataspace_id;
    }


    // Help function for creating a dataset
    public static int H5Dcreate_wrap (int loc_id, String name, int type_id,
                                      int space_id, int create_plist_id)
    {
        int dataset_id = -1;  // dataset identifier

        try
        {
            // Create the dataset
            dataset_id = H5.H5Dcreate (loc_id, name, type_id, space_id,
                    create_plist_id);
        }
        catch (HDF5Exception hdf5e)
        {
            System.out.println
                    ("CreateDataset.H5Dcreate_wrap() with HDF5Exception: "
                            + hdf5e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println
                    ("CreateDataset.H5Dcreate_wrap() with other Exception: "
                            + e.getMessage());
        }
        return dataset_id;
    }


    // Help function for ending access to the dataset and releasing
    // resources used by it.
    public static int H5Dclose_wrap (int dataset_id)
    {
        int status = -1;

        try
        {
            // End access to the dataset and release resources used by it.
            status = H5.H5Dclose (dataset_id);
        }
        catch (HDF5Exception hdf5e)
        {
            System.out.println
                    ("CreateDataset.H5Dclose_wrap() with HDF5Exception: "
                            + hdf5e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println
                    ("CreateDataset.H5Dclose_wrap() with other exception: "
                            + e.getMessage());
        }
        return status;
    }


    // Help function for terminating access to the data space.
    public static int H5Sclose_wrap (int dataspace_id)
    {
        int status = -1;

        try
        {
            // Terminate access to the data space.
            status = H5.H5Sclose (dataspace_id);
        }
        catch (HDF5Exception hdf5e)
        {
            System.out.println
                    ("CreateDataset.H5Sclose_wrap() with HDF5Exception: "
                            + hdf5e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println
                    ("CreateDataset.H5Sclose_wrap() with other exception: "
                            + e.getMessage());
        }
        return status;
    }



    // Help function for writing the dataset
    public static int H5Dwrite_wrap (int dataset_id, int mem_type_id,
                                     int mem_space_id, int file_space_id,
                                     int xfer_plist_id, Object buf)
    {
        int status = -1;

        try
        {
            // Write the dataset.
            status = H5.H5Dwrite (dataset_id, mem_type_id, mem_space_id,
                    file_space_id, xfer_plist_id, buf);
        }
        catch (HDF5Exception hdf5e)
        {
            System.out.println
                    ("DatasetRdWt.H5Dwrite_wrap() with HDF5Exception: "
                            + hdf5e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println
                    ("DatasetRdWt.H5Dwrite_wrap() with other exception: "
                            + e.getMessage());
        }
        return status;
    }


    // Help function for reading the dataset
    public static int H5Dread_wrap (int dataset_id, int mem_type_id,
                                    int mem_space_id, int file_space_id,
                                    int xfer_plist_id, Object obj)
    {
        int status = -1;

        try
        {
            // Read the dataset.
            status = H5.H5Dread (dataset_id, mem_type_id, mem_space_id,
                    file_space_id, xfer_plist_id, obj);
        }
        catch (HDF5Exception hdf5e)
        {
            System.out.println
                    ("DatasetRdWt.H5Dread_wrap() with HDF5Exception: "
                            + hdf5e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println
                    ("DatasetRdWt.H5Dread_wrap() with other exception: "
                            + e.getMessage());
        }
        return status;
    }




    // Help function for creating a dataset attribute.
    public static int H5Acreate_wrap (int loc_id, String name, int type_id,
                                      int space_id, int create_plist)
    {
        int attribute_id = -1;  // attribute identifier

        try
        {
            // Create the dataset
            attribute_id = H5.H5Acreate (loc_id, name, type_id, space_id,
                    create_plist);
        }
        catch (HDF5Exception hdf5e)
        {
            System.out.println
                    ("CreateAttribute.H5Acreate_wrap() with HDF5Exception: "
                            + hdf5e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println
                    ("CreateAttribute.H5Acreate_wrap() with other Exception: "
                            + e.getMessage());
        }
        return attribute_id;
    }


    // Help function for writing the attribute data.
    public static int H5Awrite_wrap (int attr_id, int mem_type_id,
                                     Object buf)
    {
        int status = -1;

        try
        {
            // Write the attribute data.
            status = H5.H5Awrite (attr_id, mem_type_id, buf);
        }
        catch (HDF5Exception hdf5e)
        {
            System.out.println
                    ("CreateAttribute.H5Awrite_wrap() with HDF5Exception: "
                            + hdf5e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println
                    ("CreateAttribute.H5Awrite_wrap() with other exception: "
                            + e.getMessage());
        }
        return status;
    }


    // Help function for closing the attribute
    public static int H5Aclose_wrap (int attribute_id)
    {
        int status = -1;

        try
        {
            // Close the dataset
            status = H5.H5Aclose (attribute_id);
        }
        catch (HDF5Exception hdf5e)
        {
            System.out.println
                    ("CreateAttribute.H5Aclose_wrap() with HDF5Exception: "
                            + hdf5e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println
                    ("CreateAttribute.H5Aclose_wrap() with other exception: "
                            + e.getMessage());
        }
        return status;
    }


    // Help function for creating a group named "/MyGroup" in the file.
    public static int H5Gcreate_wrap (int loc_id, String name, int size_hint)
    {
        int group_id = -1;    // group identifier
        try
        {
            // Create a group
            group_id = H5.H5Gcreate (loc_id, name, size_hint);

        }
        catch (HDF5Exception hdf5e)
        {
            System.out.println
                    ("CreateGroup.H5Gcreate_wrap() with HDF5Exception: "
                            + hdf5e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println
                    ("CreateGroup.H5Gcreate_wrap() with other Exception: "
                            + e.getMessage());
        }
        return group_id;
    }


    // Help function for closing the group
    public static int H5Gclose_wrap (int group_id)
    {
        int status = -1;

        try
        {
            // Close the group
            status = H5.H5Gclose (group_id);
        }
        catch (HDF5Exception hdf5e)
        {
            System.out.println
                    ("CreateGroup.H5Gclose_wrap() with HDF5Exception: "
                            + hdf5e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println
                    ("CreateGroup.H5Gclose_wrap() with other exception: "
                            + e.getMessage());
        }
        return status;
    }





}
