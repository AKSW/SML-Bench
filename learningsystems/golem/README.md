# Golem

## Description

[Golem](http://www.doc.ic.ac.uk/~shm/golem.html) is an ILP tool developed by [Stephen Muggleton ](http://wp.doc.ic.ac.uk/shm/).
It follows a [Relative Least General Generalisations](http://www.doc.ic.ac.uk/~shm/Papers/alt90.pdf)-based induction approach
which was published in 1990. Golem is written in C.

## Installation

To use Golem with SML-Bench, the latest source tar archive should be downloaded at
http://www.doc.ic.ac.uk/~shm/Software/golem/src.tar.gz . Since the code did not compile on our systems we recommend using the
binary executable `golem` which can also be directly downloaded at http://www.doc.ic.ac.uk/~shm/Software/golem/src/golem .
The `golem` executable then needs to be moved to `SML-Bench/learningsystems/golem/` to be found by the provided `run` script.