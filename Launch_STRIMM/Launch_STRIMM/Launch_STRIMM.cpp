#include <iostream>
#include <Windows.h>
#include <string>
#include <sstream>
#include <fstream>

using namespace std;
int main()
{
    cout << "Starting STRIMM Launcher" << endl;
    PROCESS_INFORMATION pi{};
    STARTUPINFOA si{};
    cout << "here 1" << endl;
    si.cb = sizeof(si);

    char szDir[1024] = { 0 };
    stringstream ss;
    GetCurrentDirectoryA(sizeof(szDir), szDir);
    ss << szDir;
    ss << "\\STRIMM_settings.txt";
    cout << "here 2" << endl;
    cout << ss.str() << endl;
    ifstream ifs(ss.str().c_str());
    cout << "here 3" << endl;
    if (ifs) {
        string startMemorySz;
        string startMemSize;
        ifs >> startMemorySz >> startMemSize;
        string maxMemorySz;
        string maxMemorySize;
        ifs >> maxMemorySz >> maxMemorySize;
        ifs.close();

        cout << "Start mem size is: " + startMemSize << endl;
        cout << "Max mem size is: " + maxMemorySize << endl;
        stringstream ss;
        ss << "javaw  -Xms" << startMemSize << " -Xmx" << maxMemorySize << " -splash:splash.png  -cp ./jars/* Main";
        
        if (!CreateProcessA(nullptr, (LPSTR)ss.str().c_str(), nullptr, nullptr, false, 0, nullptr, szDir, &si, &pi))
        {
            MessageBoxA(NULL, "Failure to run STRIMM : javaw error", szDir, MB_OK);
            exit(EXIT_FAILURE);
        }
        // MessageBoxA(NULL, "Succeeded to load STRIMM", szDir, MB_OK);
        CloseHandle(pi.hThread);
        ifs.close();
    }
    else {
        MessageBoxA(NULL, "Cannot find STRIMM_settings.txt", "", MB_OK);
    }
    return 0;

}