#!/bin/sh

for f in */background.xcf
do
  PNG_OUT=$(echo "$f" | sed 's/xcf$/png/g')
  echo "$f" "${PNG_OUT}"

  gimp -i --batch-interpreter=python-fu-eval -b - << EOF
import gimpfu

def convert(fileIn,fileOut):
  img = pdb.gimp_file_load(fileIn, fileIn)
  layer = pdb.gimp_image_merge_visible_layers(img, 1)
  pdb.gimp_file_save(img, layer, fileOut, fileOut)
  pdb.gimp_image_delete(img)

convert("${f}","${PNG_OUT}")
pdb.gimp_quit(1)
EOF

done
