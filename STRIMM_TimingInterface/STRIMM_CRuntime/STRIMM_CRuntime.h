#include <vector>
#include <string>
#include <unordered_map>
#include <STRIMM_TimingInterface\STRIMM_Timing.h>
#include <optional>

#define _STRIMM_C_RUNTIME_BUILD
#include <STRIMM_TimingInterface\dynamic_functions.h>


#ifndef STRIMM_CRUNTIME_H
#define STRIMM_CRUNTIME_H


namespace STRIMM {
	class CRuntime {
	public:
		::std::vector<::std::string> GetInstalledDevices();
		void addInstallDirectory(const ::std::string& directory);

		Result loadTimer(const ::std::string& deviceName, const ::std::string& deviceLabel);

		class Property
		{
		public:
			Property(TimerLibFunctions* funs) : funcs(funs) {}
			virtual PropertyCollection::PropertyType getPropertyType() { return PropertyCollection::PropertyType::UnknownPropertyType; }
		protected:
			TimerLibFunctions* funcs;
		};

		template<class c_Type>
		class AbstractProperty : public Property
		{
		public:
			AbstractProperty(proptype_from_ctype<c_Type>* prop, TimerLibFunctions* funcs) 
				: Property(funcs), instance(prop) {}

			void setValue(const c_Type& value) { 
				if constexpr (::std::is_same<c_Type, ::std::string>::value)
					(funcs->setValFun<c_Type>())(instance, value.c_str()); 
				else
					(funcs->setValFun<c_Type>())(instance, value); 
			}
			c_Type getValue() { return (funcs->getValFun<c_Type>())(instance); }

			bool isPreInit() { return funcs->IsPreInit(instance); }

			PropertyCollection::PropertyType getPropertyType() { return funcs->GetPropertyType(instance); }
		protected:
			proptype_from_ctype<c_Type>* instance;
		};

		template<class c_Type>
		class NumericProperty : public AbstractProperty<c_Type>
		{
		public:
			NumericProperty(proptype_from_ctype<c_Type>* prop, TimerLibFunctions* funcs)
				: AbstractProperty<c_Type>(prop, funcs) {}

			c_Type getMax() 
			{ 
				auto fn = this->funcs->getMaxFun<c_Type>();
				return fn(this->instance); 
			}
			c_Type getMin() 
			{ 
				auto fn = this->funcs->getMinFun<c_Type>();
				return fn(this->instance); 
			}
			void setMax(c_Type max) 
			{ 
				auto fn = this->funcs->setMaxFun<c_Type>();
				return fn(this->instance, max); 
			}
			void setMin(c_Type min) { 
				auto fn = this->funcs->setMinFun<c_Type>();
				return fn(this->instance, min); 
			}

		};

		class IntegerProperty : public NumericProperty<int>
		{
		public:
			IntegerProperty(PropertyCollection::IntegerProperty* prop, TimerLibFunctions* funcs)
				: NumericProperty(prop, funcs) {}
		};

		class FloatProperty : public NumericProperty<float> 
		{
		public:
			FloatProperty(PropertyCollection::FloatProperty* prop, TimerLibFunctions* funcs)
				: NumericProperty(prop, funcs) {}
		};

		class DoubleProperty : public NumericProperty<double> 
		{
		public:
			DoubleProperty(PropertyCollection::DoubleProperty* prop, TimerLibFunctions* funcs)
				: NumericProperty(prop, funcs) {}
		};

		class StringProperty : public AbstractProperty<::std::string> 
		{
		public:
			StringProperty(PropertyCollection::StringProperty* prop, TimerLibFunctions* funcs)
				: AbstractProperty(prop, funcs) {}

			::std::vector<::std::string> getAllowedValues()
			{
				auto out = ::std::vector<::std::string>();
				for (int i = 0; i < funcs->GetNAllowedValues(instance); i++)
				{
					if (auto str = funcs->GetAllowedValue(instance, i); str != nullptr)
						out.emplace_back(str);
				}

				return out;
			}
		};

		class BooleanProperty : public AbstractProperty<bool>
		{
		public:
			BooleanProperty(PropertyCollection::BooleanProperty* prop, TimerLibFunctions* funcs)
				: AbstractProperty(prop, funcs) {}
		};

		template<PropertyCollection::PropertyType pt>
		using cr_type_from_propType =
			typename std::conditional<pt == PropertyCollection::IntPropertyType, IntegerProperty,
			typename std::conditional<pt == PropertyCollection::FloatPropertyType, FloatProperty,
			typename std::conditional<pt == PropertyCollection::DoublePropertyType, DoubleProperty,
			typename std::conditional<pt == PropertyCollection::StringPropertyType, StringProperty, 
			typename std::conditional<pt == PropertyCollection::BooleanPropertyType, BooleanProperty, Property>::type>::type>::type>::type>::type;

		template<PropertyCollection::PropertyType pt>
		using getPropReturnType =
			typename ::std::conditional<pt == PropertyCollection::UnknownPropertyType,
				::std::pair<::std::unique_ptr<cr_type_from_propType<pt>>, PropertyCollection::PropertyType>,
				::std::unique_ptr<cr_type_from_propType<pt>>>::type;

		struct TimerDevice {
			::std::string libraryName;

			TimerDevice(const ::std::string& libName);
			TimerDevice(const TimerDevice& that) = delete;
			TimerDevice(TimerDevice&& that) = delete;
			String& operator=(String that) = delete;
			~TimerDevice();

			::std::string getTimerName();
			int getAPIVersion();
			::std::string getLastError();

			Result initialise();
			std::vector<std::string> getAvailableChannelNames(AbstractTimer::ChannelType type);
			std::vector<std::string> getChannelNames(AbstractTimer::ChannelType type);
			std::vector<std::string> getChannelsInUse(AbstractTimer::ChannelType type);

			Result addAnalogueInput(const std::string& channelName, const STRIMM_AIDesc& desc);
			Result addDigitalInput(const std::string& channelName, const STRIMM_DIDesc& desc);
			Result addAnalogueOutput(const std::string& channelName, const STRIMM_AODesc& desc);
			Result addDigitalOutput(const std::string& channelName, const STRIMM_DODesc& desc);
			
			Result removeAnalogueInput(const std::string& channelName);
			Result removeDigitalInput(const std::string& channelName);
			Result removeAnalogueOutput(const std::string& channelName);
			Result removeDigitalOutput(const std::string& channelName);

			void registerStopCallback(STRIMM_StopCallback callback, void* callbackInfo);
			void registerErrorCallback(STRIMM_ErrorCallback callback, void* callbackInfo);

			Result prime();
			Result start();
			Result stop();

			::std::vector<::std::string> getPropertyNames();
			
			template<PropertyCollection::PropertyType pt>
			getPropReturnType<pt> getProperty(const ::std::string& propertyName)
			{
				PropertyCollection::Property* dll_prop = funcs.GetProperty(instance, propertyName.c_str());

				if constexpr (pt == PropertyCollection::UnknownPropertyType)
				{
					auto propType = funcs.GetPropertyType(dll_prop);
					switch (propType)
					{
					case PropertyCollection::IntPropertyType:
						return ::std::make_pair(::std::make_unique<IntegerProperty>(dynamic_cast<PropertyCollection::IntegerProperty*>(dll_prop), &funcs), propType);
					case PropertyCollection::FloatPropertyType:
						return ::std::make_pair(::std::make_unique<FloatProperty>(dynamic_cast<PropertyCollection::FloatProperty*>(dll_prop), &funcs), propType);
					case PropertyCollection::DoublePropertyType:
						return ::std::make_pair(::std::make_unique<DoubleProperty>(dynamic_cast<PropertyCollection::DoubleProperty*>(dll_prop), &funcs), propType);
					case PropertyCollection::StringPropertyType:
						return ::std::make_pair(::std::make_unique<StringProperty>(dynamic_cast<PropertyCollection::StringProperty*>(dll_prop), &funcs), propType);
					case PropertyCollection::BooleanPropertyType:
						return ::std::make_pair(::std::make_unique<BooleanProperty>(dynamic_cast<PropertyCollection::BooleanProperty*>(dll_prop), &funcs), propType);
					}
					return ::std::make_pair(::std::unique_ptr<cr_type_from_propType<pt>>(nullptr), PropertyCollection::UnknownPropertyType);
				}
				else
				{
					if (funcs.GetPropertyType(dll_prop) == pt)
						return ::std::make_unique<cr_type_from_propType<pt>>(dynamic_cast<PropertyCollection::type_from_propType<pt>*>(dll_prop), &funcs);
					return ::std::unique_ptr<cr_type_from_propType<pt>>(nullptr);
				}
			}

			
			bool isInstanceValid();
		private:

			LibType libraryHandle;
			TimerLibFunctions funcs;
			STRIMM::AbstractTimer* instance;

		};

		TimerDevice* getDevicePointer(const ::std::string& deviceLabel);
	private:

		void loadDynamicLibrary(const ::std::string& filename, ::std::vector<::std::string>* devices);

		::std::vector<::std::string> installDirectories = ::std::vector<::std::string>();
		::std::unordered_map<::std::string, ::std::string> deviceLocations;
		::std::unordered_map<::std::string, TimerDevice> loadedDevices = ::std::unordered_map<::std::string, TimerDevice>();
	};
}

#endif // !STRIMM_CRUNTIME_H

