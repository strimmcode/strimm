#ifndef _STRIMM_NIDAQ_RANGE_H
#define _STRIMM_NIDAQ_RANGE_H
#include <stdexcept>

template<class T>
class InclusiveRange
{
public:
	InclusiveRange() : _end(0), _start(0), valid(false) {}
	InclusiveRange(T start, T end) 
		: _start(start), _end(end), valid(true) { if (start > end) throw std::invalid_argument("Start must be less than end!"); }

	T size() const { return valid ? (_end - _start) : 0; }
	bool contains(T i) const { return valid ? ((i <= _end) && (i >= _start)) : false; }
	bool overlaps(const InclusiveRange<T>& other) const { return valid ? (other.contains(_start) || other.contains(_end)) : false; }
	InclusiveRange expanded(T i) { return valid ? InclusiveRange(expandedStart(i), expandedEnd(i)) : InclusiveRange(i, i); }
	void expandRange(T i) {
		if (valid)
		{
			_start = expandedStart(i);
			_end = expandedEnd(i);
		}
		else
		{
			_start = _end = i;
			valid = true;
		}
	}
private:
	inline T expandedStart(T i) const { return (i < _start) ? i : _start; }
	inline T expandedEnd(T i) const { return (i > _end) ? i : _end; }

	int _start, _end;
	bool valid;
};

#endif
