#ifndef _STRIMM_PROPERTY_COLLECTION_H
#define _STRIMM_PROPERTY_COLLECTION_H

#include "STRIMMString.h"

namespace STRIMM
{
	class PropertyCollection
	{
	public:
		enum PropertyType {
			IntPropertyType = 0,
			DoublePropertyType = 1,
			FloatPropertyType = 2,
			BooleanPropertyType = 3,
			StringPropertyType = 4,
			UnknownPropertyType = 5
		};

		int getNProperties();
		const String& getPropertyName(int i);

		class Property
		{
			friend class PropertyCollection;
		public:
			virtual PropertyType getPropertyType() { return UnknownPropertyType; }
			const bool isPreInit;

		protected:
			Property(bool preInit = false) : isPreInit(preInit) {}
		};

		template<class T>
		class AbstractProperty : public Property
		{
		public:
			virtual void setValue(const T& value) { _value = value; }
			virtual const T& getValue() { return _value; }

		protected:
			AbstractProperty(T defaultValue, bool isPreInit = false) 
				: Property(isPreInit) { _value = defaultValue; }
			T _value;
		};

		template<class T>
		class NumericProperty : public AbstractProperty<T>
		{
		public:
			void setValue(const T& value)
			{
				if (value >= _min && value <= _max)
					AbstractProperty<T>::setValue(value);
			}

			void setMax(T max) { _max = max; }
			T getMax() { return _max; }
			void setMin(T min) { _min = min; }
			T getMin() { return _min; }

		protected:
#ifdef max
#undef max
			T _max = ::std::numeric_limits<T>::max();
#define max(a,b)            (((a) > (b)) ? (a) : (b))
#else
			T _max = ::std::numeric_limits<T>::max();
#endif

			T _min = ::std::numeric_limits<T>::lowest();

			NumericProperty(bool isPreInit = false) : AbstractProperty<T>(0, isPreInit) {}
			NumericProperty(T defaultValue, bool isPreInit = false) : AbstractProperty<T>(defaultValue, isPreInit) {}
		};

		class IntegerProperty : public NumericProperty<int>
		{
			friend class PropertyCollection;
		public:
			IntegerProperty(bool isPreInit = false)
				: NumericProperty(isPreInit) {}
			PropertyType getPropertyType() { return IntPropertyType; }
		};

		class DoubleProperty : public NumericProperty<double>
		{
			friend class PropertyCollection;
		public:
			DoubleProperty(bool isPreInit = false)
				: NumericProperty(isPreInit) {}
			PropertyType getPropertyType() { return DoublePropertyType; }
		};

		class FloatProperty : public NumericProperty<float>
		{
			friend class PropertyCollection;
		public:
			FloatProperty(bool isPreInit = false)
				: NumericProperty(isPreInit) {}
			PropertyType getPropertyType() { return FloatPropertyType; }
		};

		class StringProperty : public AbstractProperty<String>
		{
			friend class PropertyCollection;
		public:
			StringProperty(bool isPreInit = false) : AbstractProperty("", isPreInit) {}
			StringProperty(String defaultValue, bool isPreInit = false) : AbstractProperty(defaultValue.getValue(), isPreInit) {}

			
			void setValue(const String& value)
			{
				if (allowedValues.size() == 0 || std::find(allowedValues.begin(), allowedValues.end(), value) != allowedValues.end())
					AbstractProperty::setValue(value);
			}


			void addAllowedValue(const String& value) { allowedValues.push_back(value); }
			void removeAllowedValue(const String& value) 
			{ 
				allowedValues.erase(::std::remove(allowedValues.begin(), allowedValues.end(), value), allowedValues.end()); 
			}

			int getNAllowedValues() { return allowedValues.size(); }
			const String& getAllowedValue(int i) 
			{ 
				if (i >= 0 && i < allowedValues.size())
					return allowedValues[i];
				else
					return std::move(String());
			}

			PropertyType getPropertyType() { return StringPropertyType; }
		private:
			::std::vector<String> allowedValues = ::std::vector<String>();
		};

		class BooleanProperty : public AbstractProperty<bool>
		{
			friend class PropertyCollection;
		public:
			BooleanProperty(bool isPreInit = false) : AbstractProperty(false, isPreInit) {}
			PropertyType getPropertyType() { return BooleanPropertyType; }
		};


		template<PropertyType pt>
		using type_from_propType =
			typename std::conditional<pt == IntPropertyType, IntegerProperty,
			typename std::conditional<pt == FloatPropertyType, FloatProperty,
			typename std::conditional<pt == DoublePropertyType, DoubleProperty,
			typename std::conditional<pt == StringPropertyType, StringProperty, 
			typename std::conditional<pt == BooleanPropertyType, BooleanProperty, void>::type>::type>::type>::type>::type;

		template<PropertyType pt>
		using ctype_from_propType =
			typename std::conditional<pt == IntPropertyType, int,
			typename std::conditional<pt == FloatPropertyType, float,
			typename std::conditional<pt == DoublePropertyType, double,
			typename std::conditional<pt == StringPropertyType, STRIMM::String, 
			typename std::conditional<pt == BooleanPropertyType, bool, void>::type>::type>::type>::type>::type;

		Property* getProperty(const char * name);

		template<PropertyType pt>
		type_from_propType<pt>* getPropertyAs(const String& name)
		{
			const String str = String(name);

			auto it = std::find_if(_properties.begin(), _properties.end(),
				[&str](const std::unordered_map<String, std::unique_ptr<Property>>::value_type& pair) { return pair.first == str; });

			if (it != _properties.end())
				return dynamic_cast<type_from_propType<pt>*>(it->second.get());
			else
				return nullptr;
		}

		template<PropertyType pt>
		std::unique_ptr<type_from_propType<pt>> createProperty(bool isPreInit = false)
		{
			return std::move(std::make_unique<type_from_propType<pt>>(isPreInit));
		}

	protected:

		template<class PropType>
		void registerProperty(String name, std::unique_ptr<PropType> prop)
		{
			_properties.emplace(std::make_pair(name, std::move(prop)));
			_propertyNames.push_back(name);
		}

	private:
		::std::unordered_map<String, std::unique_ptr<Property>> _properties = ::std::unordered_map<String, std::unique_ptr<Property>>();
		::std::vector<String> _propertyNames = ::std::vector<String>();
	};


}
#endif