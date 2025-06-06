netlib-java
===========

`netlib-java` is a wrapper for low-level [BLAS](http://en.wikipedia.org/wiki/Basic_Linear_Algebra_Subprograms),
[LAPACK](http://en.wikipedia.org/wiki/LAPACK) and [ARPACK](http://en.wikipedia.org/wiki/ARPACK)
that performs **as fast as the C / Fortran interfaces** with a pure JVM fallback.

Unfortunately, `netlib-java` is no longer maintained by the original author fommil. What you are looking at is a modified copy of the latest state of the software available at https://github.com/fommil/netlib-java. The main reason for making this copy and modifying it was to add support for Apple ARM.

**Note that only the osx-specific parts of the project have been updated so that they can be built successfully!**

To generate the necessary .jar files, etc., the steps in the terminal are

```
cd generator
mvn install
cd ..
mvn install
```

and then, to generate platform-specific ref and system .jar files, e.g., on Intel Macs:

```
cd native_system
mvn install
cd osx-x86_64
mvn install
cd ../..
```

The required files can then be taken from the Maven .m2 folder in the user home. 

In `netlib-java`, implementations of BLAS/LAPACK/ARPACK are provided by:

* delegating builds that use machine optimised system libraries (see below)
* self-contained native builds using the reference Fortran from [netlib.org](http://www.netlib.org)
* [F2J](http://icl.cs.utk.edu/f2j/) to ensure full portability on the JVM

The [JNILoader](https://github.com/fommil/jniloader) will attempt to load the implementations in this order automatically.

All major operating systems are supported out-of-the-box:

* OS X (`x86_64`, `aarch64`)
* Linux (`i686`, `x86_64`, `aarch64`) (**must have `libgfortran5` installed**)
* Windows (32 and 64 bit)


Machine Optimised System Libraries
==================================

High performance BLAS / LAPACK are available
[commercially and open source](http://en.wikipedia.org/wiki/Basic_Linear_Algebra_Subprograms#Implementations)
for specific CPU chipsets. It is worth noting that "optimised" here means a lot more than simply changing
the compiler optimisation flags: specialist assembly instructions are combined with [compile time profiling](http://en.wikipedia.org/wiki/Automatically_Tuned_Linear_Algebra_Software#Optimization_approach)
and the [selection of array alignments for the kernel and CPU combination](http://en.wikipedia.org/wiki/Automatically_Tuned_Linear_Algebra_Software#Can_it_afford_to_copy.3F).

An alternative to optimised libraries is to use the GPU:
e.g. [cuBLAS](https://developer.nvidia.com/cublas) or [clBLAS](https://github.com/clMathLibraries/clBLAS).
Setting up cuBLAS must be done via [our NVBLAS instructions](https://github.com/fommil/netlib-java/wiki/NVBLAS), since cuBLAS does not implement the actual BLAS API out of the box.

Be aware that GPU implementations have severe performance degradation for small arrays.
[MultiBLAS](https://github.com/fommil/multiblas) is an initiative to work around
the limitation of GPU BLAS implementations by selecting the optimal implementation
at runtime, based on the array size.

**To enable machine optimised natives in `netlib-java`, end-users make their machine-optimised `libblas3` (CBLAS) and
`liblapack3` (Fortran) available as shared libraries at runtime.**

If it is not possible to provide a shared library, [the author](https://github.com/fommil/) may be available
to assist with custom builds (and further improvements to `netlib-java`) on a commercial basis.
Make contact for availability (budget estimates are appreciated).

OS X
----

Apple OS X requires no further setup because OS X ships with the [veclib framework](https://developer.apple.com/documentation/Performance/Conceptual/vecLib/),
boasting incredible CPU performance that is difficult to surpass.


Linux
-----

(includes Raspberry Pi)

Generically-tuned ATLAS and OpenBLAS are available with most distributions (e.g. [Debian](https://wiki.debian.org/DebianScience/LinearAlgebraLibraries)) and must be enabled
explicitly using the package-manager. e.g. for Debian / Ubuntu one would type

    sudo apt-get install libatlas3-base libopenblas-base
    sudo update-alternatives --config libblas.so
    sudo update-alternatives --config libblas.so.3
    sudo update-alternatives --config liblapack.so
    sudo update-alternatives --config liblapack.so.3

selecting the preferred implementation.

However, these are only generic pre-tuned builds. To get optimal performance for a specific
machine, it is best to compile locally by grabbing the [latest ATLAS](http://sourceforge.net/projects/math-atlas/files/latest/download) or the [latest OpenBLAS](https://github.com/xianyi/OpenBLAS/archive/master.zip) and following the compilation
instructions (don't forget to turn off CPU throttling and power management during the build!).
Install the shared libraries into a folder that is seen by the runtime linker (e.g. add your install
folder to `/etc/ld.so.conf` then run `ldconfig`) ensuring that `libblas.so.3` and `liblapack.so.3`
exist and point to your optimal builds.

If you have an [Intel MKL](http://software.intel.com/en-us/intel-mkl) licence, you could also
create symbolic links from `libblas.so.3` and `liblapack.so.3` to `libmkl_rt.so` or use
Debian's alternatives system:

```
sudo update-alternatives --install /usr/lib/libblas.so     libblas.so     /opt/intel/mkl/lib/intel64/libmkl_rt.so 1000
sudo update-alternatives --install /usr/lib/libblas.so.3   libblas.so.3   /opt/intel/mkl/lib/intel64/libmkl_rt.so 1000
sudo update-alternatives --install /usr/lib/liblapack.so   liblapack.so   /opt/intel/mkl/lib/intel64/libmkl_rt.so 1000
sudo update-alternatives --install /usr/lib/liblapack.so.3 liblapack.so.3 /opt/intel/mkl/lib/intel64/libmkl_rt.so 1000
```

and don't forget to add the MKL libraries to your `/etc/ld.so.conf`
file (and run `sudo ldconfig`), e.g. add

```
/opt/intel/lib/intel64
/opt/intel/mkl/lib/intel64
```

*NOTE: Some distributions, such as Ubuntu `precise` do not create the necessary symbolic links
`/usr/lib/libblas.so.3` and `/usr/lib/liblapack.so.3` for the system-installed implementations,
so they must be created manually.*

Windows
-------

The `native_system` builds expect to find `libblas3.dll` and `liblapack3.dll` on the `%PATH%`
(or current working directory).
Besides vendor-supplied implementations,
OpenBLAS provide [generically tuned binaries](http://sourceforge.net/projects/openblas/files/),
and it is possible to build
[ATLAS](http://math-atlas.sourceforge.net/atlas_install/node54.html).

Use [Dependency Walker](http://www.dependencywalker.com) to help resolve any problems such as:
`UnsatisfiedLinkError (Can't find dependent libraries)`.

*NOTE: OpenBLAS [doesn't provide separate libraries](https://github.com/xianyi/OpenBLAS/issues/296)
so you will have to customise the build or copy the binary into both `libblas3.dll` and
`liblapack3.dll` whilst also obtaining a copy of `libgfortran-1-3.dll`, `libquadmath-0.dll` and
`libgcc_s_seh-1.dll` from [MinGW](http://www.mingw.org).*


Customisation
=============

A specific implementation may be forced like so:

* `-Dcom.github.fommil.netlib.BLAS=com.github.fommil.netlib.NativeRefBLAS`
* `-Dcom.github.fommil.netlib.LAPACK=com.github.fommil.netlib.NativeRefLAPACK`
* `-Dcom.github.fommil.netlib.ARPACK=com.github.fommil.netlib.NativeRefARPACK`

A specific (non-standard) JNI binary may be forced like so:

* `-Dcom.github.fommil.netlib.NativeSystemBLAS.natives=netlib-native_system-myos-myarch.so`

(note that this is **not** your `libblas.so.3` or `liblapack.so.3`, it is the `netlib-java` native wrapper component which automatically detects and loads your system's libraries).

To turn off natives altogether, add these to the JVM flags:

* `-Dcom.github.fommil.netlib.BLAS=com.github.fommil.netlib.F2jBLAS`
* `-Dcom.github.fommil.netlib.LAPACK=com.github.fommil.netlib.F2jLAPACK`
* `-Dcom.github.fommil.netlib.ARPACK=com.github.fommil.netlib.F2jARPACK`



