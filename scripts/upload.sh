#!/bin/sh

rsync -acv --partial --progress target/release/spazradio.apk ken@spaz.org:~/public_html/public/
