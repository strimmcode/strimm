#include <iostream>
#include <Windows.h>
using namespace std;
typedef int(__stdcall* FN_PTR)();

int main() {

	HINSTANCE hGetProcIDDLL = LoadLibrary(L"Test.dll");
	FN_PTR TestFn = (FN_PTR)GetProcAddress(hGetProcIDDLL, "Test");
	cout << TestFn() << endl;

	return 0;

}