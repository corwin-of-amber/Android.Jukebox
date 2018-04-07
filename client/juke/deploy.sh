APP_NAME=juke
NWJS_VER='0.27.0-beta1-sdk'

OUTDIR=/tmp/$APP_NAME

shopt -s extglob

rm -rf $OUTDIR
mkdir $OUTDIR
cp -r ~/.nwjs/$NWJS_VER/nwjs.app $OUTDIR/$APP_NAME.app
mkdir $OUTDIR/$APP_NAME.app/Contents/Resources/app.nw
cp -r !(node_modules|bower_components) $OUTDIR/$APP_NAME.app/Contents/Resources/app.nw
( cd $OUTDIR/$APP_NAME.app/Contents/Resources/app.nw && npm install && bower install )
( cd $OUTDIR && 7z a $APP_NAME.zip $APP_NAME.app && mv $APP_NAME.zip ~/var/Dropbox/Public/Code/$APP_NAME.zip )
