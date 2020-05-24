#!/bin/sh -ex

rm -rfv out
mkdir -p out

for name in $(cat data.txt)
do
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

  pushd "out/${name}"
  inkscape \
  --export-type=png \
  --export-width=1280 \
  --export-height=640 \
  --export-filename=out.png \
  out.svg
  convert out.png out.jpg
  popd
done
