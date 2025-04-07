#!/bin/bash

for A in l/lapack/libblas3_3.10.0-2_i386.deb l/lapack/libblas-dev_3.10.0-2_i386.deb l/lapack/liblapack-dev_3.10.0-2_i386.deb l/lapack/liblapack3_3.10.0-2_i386.deb ; do
    curl -O "https://mirrors.edge.kernel.org/debian/pool/main/$A"
    ar vx `basename $A`
    sudo tar xJf data.tar.xz -C /opt/linux-i686/
done
