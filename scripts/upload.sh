#!/bin/sh

rsync -acv --partial --progress target/spazradio.apk ken@spaz.org:~/public_html/public/
