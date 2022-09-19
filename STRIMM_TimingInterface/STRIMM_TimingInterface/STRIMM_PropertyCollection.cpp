#include <functional>
#include <algorithm>
#include "STRIMM_PropertyCollection.h"

namespace STRIMM
{
	int PropertyCollection::getNProperties()
	{
		return _propertyNames.size();
	}


	const String& PropertyCollection::getPropertyName(int i)
	{
		if (i < 0 || i >= _propertyNames.size())
			return String("");

		return _propertyNames[i];
	}

	PropertyCollection::Property* PropertyCollection::getProperty(const char * name)
	{
		const String str = String(name);

		auto it = std::find_if(_properties.begin(), _properties.end(),
			[&str](const std::unordered_map<String, std::unique_ptr<Property>>::value_type& pair) { return pair.first == str; });

		if (it != _properties.end())
			return it->second.get();
		else
			return nullptr;
	}
}