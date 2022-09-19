#ifndef STRIMM_STRING_H
#define STRIMM_STRING_H

#include <unordered_map>


namespace STRIMM
{
	/**
	*	@deprecated Wrapper for C character array that was initially used when attempting to avoid issues with passing objects over DLL boundaries. 
	*	To be replaced with std::string in the future.
	*/
	class String
	{
	public:
		String();
		String(const char * str);
		String(const String& that);
		String(String&& that);
		~String();

		const char * getValue() const;

		friend void swap(String& first, String& second);
		friend bool operator==(const String &lhs, const String &rhs);
		String& operator=(String that);
	private:
		char * value;
	};
}

namespace std
{
	/**
	* @deprecated Implementation of ELF hash to allow STRIMM::Strings to be used in std::unordered_maps
	*/
	template<>
	struct hash<STRIMM::String>
	{
		std::size_t operator()(const STRIMM::String& strIn) const 
		{
			unsigned int hash = 0;
			unsigned int x    = 0;

			const char* str = strIn.getValue();
			size_t length = std::strlen(str);

			for (size_t i = 0; i < length; ++str, ++i)
			{
				hash = (hash << 4) + (*str);
				if ((x = hash & 0xF0000000L) != 0)
				{
					hash ^= (x >> 24);
				}

				hash &= ~x;
			}

			return hash;
		}
	};
}

#endif // !STRIMM_STRING_H

