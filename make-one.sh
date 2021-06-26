#!/bin/sh -ex

if [ $# -ne 1 ]
then
  echo "usage: name" 1>&2
  exit 1
fi

name="$1"
shift

PROJECT_URL=$(cat "src/${name}/url.txt")
PROJECT_DESCRIPTION=$(cat "src/${name}/description.txt")

mkdir -p "out/${name}"
rsync -av "src/${name}/" "out/${name}/"
saxon \
-xsl:src/social.xsl \
-s:src/social.svg \
projectName="${name}" \
projectDescription="${PROJECT_DESCRIPTION}" \
projectURL="${PROJECT_URL}" > "out/${name}/out.svg"

if [ -f "src/${name}/cover.png" ]
then
  saxon \
  -xsl:src/book_cover.xsl \
  -s:src/book_cover.svg \
  projectName="${name}" \
  projectDescription="User Manual" > "out/${name}/book_cover.svg"
fi

pushd "out/${name}"
inkscape \
--export-type=png \
--export-width=1280 \
--export-height=640 \
--export-filename=out.png \
out.svg
convert out.png out.jpg

if [ -f "book_cover.svg" ]
then
  inkscape \
  --export-type=png \
  --export-width=600 \
  --export-height=800 \
  --export-filename=book_cover.png \
  book_cover.svg
  convert book_cover.png book_cover.jpg
fi
popd

mv "out/${name}/out.jpg" "out/${name}.jpg"

if [ -f "src/${name}/cover.png" ]
then
  mv "out/${name}/book_cover.jpg" "out/${name}_book_cover.jpg"
fi
