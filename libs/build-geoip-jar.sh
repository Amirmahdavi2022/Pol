#!/usr/bin/env sh

BASE=$(dirname "$0")

cd "$BASE" || exit

rm -rf assets

mkdir assets1
cd assets1

echo "downloading geoip from:"
echo "https://gitlab.torproject.org/tpo/core/tor/-/raw/main/src/config/geoip?ref_type=heads"
curl https://gitlab.torproject.org/tpo/core/tor/-/raw/main/src/config/geoip?ref_type=heads > geoip

echo ""
echo "downloading geoip6 from:"
echo "https://gitlab.torproject.org/tpo/core/tor/-/raw/main/src/config/geoip6?ref_type=heads"
curl https://gitlab.torproject.org/tpo/core/tor/-/raw/main/src/config/geoip6?ref_type=heads > geoip6
cd ..

mkdir assets
mv assets1 assets/assets

python3 reproducible_zip.py assets geoip_new.jar

sha256sum geoip.jar geoip_new.jar
rm -f geoip.jar
mv geoip_new.jar geoip.jar

rm -rf assets
