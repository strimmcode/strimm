#include "STRIMMString.h"
#include <cstring>
#include <string.h>

namespace STRIMM {

	String::String() : value(nullptr) {}

	String::String(const char * str)
	{
		if (str == nullptr)
		{
			value = nullptr;
		}
		else
		{
			size_t len = std::strlen(str) + 1;
			value = new char[len];
			strcpy_s(value, len, str);
		}
	}

	String::String(const String & that)
	{
		if (that.value == nullptr)
		{
			value = nullptr;
		}
		else
		{
			size_t size = strlen(that.value) + 1;
			value = new char[size];
			strcpy_s(value, size, that.value);
		}
	}

	String::String(String && that)
		: String()
	{
		swap(*this, that);
	}

	String::~String()
	{
		if (value) {
			delete[] value;
			value = nullptr;
		}
	}

	const char * String::getValue() const { return value; }

	String & String::operator=(String that)
	{
		swap(*this, that);
		return *this;
	}

	void swap(String & first, String & second)
	{
		auto tmp = first.value;
		first.value = second.value;
		second.value = tmp;
	}

	bool operator==(const String & lhs, const String & rhs)
	{
		if (!lhs.value || !rhs.value)
			return false;

		return !std::strcmp(lhs.value, rhs.value);
	}
}