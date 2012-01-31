### How to Build TME

#### Red Hat Based

Prerequisites packages:

* Sun JDK (jdk-1.6.0_21+)
* Apache Ant (1.8.2+)
* GNU g++ (gcc-c++)
* CMake (cmake)
* Boost (boost-devel)
* rpmbuild (rpm-build)

Make sure that the binary executable of ant is included in the search path, and it uses the Sun JDK to build.

Simply run "make rpm" in the top directory, and all the RPM packages of TME will be generated to "output/artifacts"

