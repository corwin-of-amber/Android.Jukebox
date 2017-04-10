fs = require('fs')
ytdl = require('ytdl-core')
ffmpeg = require('fluent-ffmpeg')

MemoryStream = require('memory-stream')
stream = require('stream')

youtube-play = (url) ->
    fmts = []
    <- ytdl.getInfo(url).then
    for fmt in it.formats
      console.log "#{fmt.audioEncoding}/#{fmt.encoding}  #{fmt.audioBitrate}   #{fmt.url}"
      if fmt.audioEncoding in ['vorbis', 'aac'] && fmt.encoding == null
        fmts.push fmt

    fmt = fmts.[0]
    if !fmt?
      console.warn("No suitable audio format found."); return
    try
      #s = fs.createWriteStream("/tmp/hat.mp3")
      s = youtube-play.stream = new stream.PassThrough
      ytdl(url, {format: fmt})
        ffmpeg(..).audioCodec('libmp3lame').format("mp3")
        .on 'start' -> console.log '[ffmpeg] start'
        .on 'end' -> console.log '[ffmpeg] end'
        #.pipe()
        #  .once 'data' ->
        #    console.log 'data', @
        .pipe(s, {end: true})

      $.ajax 'http://192.168.0.7:2222/play?http://192.168.0.3:8000/hat.mp3'

          #..on 'progress' !->
          #  console.log('Processing: ')# + it.percent + '% done')
      #.pipe(fs.createWriteStream("/tmp/hat.ogg"))
    catch e
      console.error e



get-my-ip = ->
  os = require('os')
  ifaces = os.networkInterfaces()

  addresses = []
    for name, iface of ifaces
      for entry in iface
        if (entry.family == 'IPv4' && !entry.internal)
           ..push entry.address

  addresses[0] ? 'localhost'


#youtube-play URL

#express = require('express')
#app = express()
http = require 'http'
server = http.createServer()


PORT = 8000

server.on 'request' (request, response) ->
  #fs.createReadStream("/tmp/link.html").pipe(response)
  youtube-play.stream.pipe(response)

server.listen PORT, ->
  console.log "Express server listening on http://#{get-my-ip!}:#{server.address!port}"

window.addEventListener 'unload' -> server.close!

export ytdl, server, youtube-play, URL
