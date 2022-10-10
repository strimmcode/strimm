#include <iostream>
#ifdef _WIN32
#include <Windows.h>
#include <string>
#else
#endif

using namespace std;
int main()
{
#ifdef _WIN32
    PROCESS_INFORMATION pi{};
    STARTUPINFOA si{};

    si.cb = sizeof(si);

    char szDir[1024];
    GetCurrentDirectoryA(sizeof(szDir), szDir);

    if (!CreateProcessA(nullptr, "javaw -Xmx16384m -Xms16384m -splash:./splash.png -cp ./jars/* uk.co.strimm.Main", nullptr, nullptr, false, 0, nullptr, szDir, &si, &pi))
    {
        std::cout << "Failed to launch STRIMM!" << std::endl;
        exit(EXIT_FAILURE);
    }

    CloseHandle(pi.hThread);
    return 0;
#else
    std::cout << "STRIMM is currently only supported on Windows" << std::endl;
    return 0;
#endif
}