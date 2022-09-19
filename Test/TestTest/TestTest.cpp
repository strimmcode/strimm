#include <iostream>
#include <Windows.h>
using namespace std;
typedef int(__stdcall* FN_PTR)();

int main() {

	HINSTANCE hGetProcIDDLL = LoadLibrary(L"Test.dll");
	FN_PTR fff = (FN_PTR)GetProcAddress(hGetProcIDDLL, "Test");
	cout << fff() << endl;

	return 0;

}