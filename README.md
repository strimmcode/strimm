# STRÏMM
# Welcome to STRÏMM
![STRÏMM](logo/main_strimm_logo_with_text.png)

## What is STRÏMM?
STRÏMM (**S**ynchronised **T**race **R**ecording **I**n **I**mageJ and **M**icro**M**anager, pronounced like "stream") is a software application designed to perform synchronous acquisition of data from multiple, heterogeneous data sources into one program allowing users to view incoming data in real time. Data is then saved in easy to use formats for further offline analysis. STRÏMM supports experiments with synchronous and asynchronous paradigms, multiple cameras, closed loop logic, and episodic acquisition.

Under the hood STRÏMM is a Kotlin application (100% interoperable with Java) with the open source softwares of [ImageJ 2.0](https://imagej.net/) and [MicroManager 2.0](https://micro-manager.org/wiki/Version_2.0) being utilised within STRÏMM. The use of these programs and other areas such as the GUI and other features is underpinned by an actor-based framework called [Akka](https://akka.io/). This framework is responsible for handling all incoming data and running experiments. It does this through an actor based concept where actors are assigned roles and given messages to carry out specific tasks. It is lightweight, and has many features built in like thread safety and scalability.

## Personnel
STRÏMM is a collaborative project between individuals at the University of St Andrews, University of Sheffield, and [Cairn Research Ltd](https://www.cairn-research.co.uk/)

### Developers
* [Jacob Francis](https://pulverlab.wp.st-andrews.ac.uk/people/) (University of St Andrews)  
* [Terry Wright]
* [Elliot Steele](https://ashleycadby.staff.shef.ac.uk/authors/elliot/) (University of Sheffield)  

### Contributors
#### Cairn Research Ltd
* Jeremy Graham  
#### University of St Andrews
* Stefan Pulver  
* James Macleod  
#### University of Sheffield
* Ash Cadby
