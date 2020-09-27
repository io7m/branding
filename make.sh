#!/bin/sh -ex

rm -rfv out
mkdir -p out

for name in $(cat data.txt)
do
  ./make-one.sh "${name}"
done
