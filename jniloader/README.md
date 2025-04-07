jniloader
=========

Simple (for the end user) native library loader.

The static method in `JniLoader` enables developers to ship native
libraries in jar files which are extracted and loaded at runtime,
making native loading transparent for the end-user (as long as
they are on a platform supported by you).

Native libraries can also be placed in `java.library.path` for
slightly faster startup times (tens of milliseconds during JVM startup).

Unfortunately, this package is no longer maintained by the original author fommil. This local copy of the corresponding github repository was made to add macOS/OS X aarch64 as an architecture so that netlib-java can be used on that platform. 



