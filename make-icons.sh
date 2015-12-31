outdir=app/src/main/res/drawable-hdpi
triangle_up='polygon 36,12 9,47 62,47'
triangle_up_squash='polygon 36,5 11,37 60,37'
block_below='polygon 9,44 62,44 62,52 9,52'
convert -size 72x72 canvas:none -fill black -draw "$triangle_up" $outdir/knob_stopped.png
convert -size 72x72 canvas:none -fill black -draw "$triangle_up" -rotate 90 $outdir/knob_playing.png
convert -size 72x72 canvas:none -fill black -draw "$triangle_up_squash" -draw "$block_below" -rotate 90 $outdir/knob_paused.png
convert -size 72x72 canvas:none -fill black -draw "$triangle_up_squash" -distort AffineProjection '1,0,0,1,0,25' -draw \
"$triangle_up_squash" -distort AffineProjection '1,0,0,1,0,-5' -rotate 90 $outdir/knob_forward.png
