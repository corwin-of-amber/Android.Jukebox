fs = require 'fs'
path = require 'path'
mkdirp = require 'mkdirp'
ffmpeg = require 'fluent-ffmpeg'

MEDIA_EXTENSIONS = <[mp3 opus ogg m4a wma]>

/**
 * Example: set-album-metadata("Music/My Album", {artist: 'Richard Clayderman', album: 'Gold Selection'})
 */
set-album-metadata = (input-spec, metadata, out-dir=void) ->

  ext = MEDIA_EXTENSIONS.join('|')

  root-dir = get-root-dir(input-spec) ? '.'
  input-files = find-input-files(input-spec)

  out-dir = path.join(root-dir, 'out')
  mkdirp.sync out-dir

  console.log "Writing to: #{out-dir}"

  Promise.chain do
    for let in-fn in input-files
      base = path.basename(in-fn).replace //([.]?-[a-zA-Z0-9_]+)?[.](#{ext})$// (_, , ext)->
        ext = {'opus': 'ogg'}[ext] ? ext
        ".#{ext}"
      out-fn = path.join(out-dir, base)
      ->
        console.log out-fn
        set-metadata in-fn, out-fn, metadata
        .then -> if metadata.track then metadata.track++

Promise.chain = (ops) ->
    ops.reduce (p, f) -> p.then f
    , Promise.resolve!

set-metadata = (infile, outfile, metadata-props={}) ->

  metadata-flag = '-metadata'  # note: in the past, -metadata:s:0 was needed for some ogg streams
  metadata-options = [].concat \
    ...[[metadata-flag, "#{k}=#{v}"] for k, v of metadata-props]

  if metadata-options.length == 0
    metadata-options = [[]]  # ffmpeg.outputOptions is not fond of empty argument list

  new Promise (resolve, reject) ->
    <- configure-ffmpeg
    ffmpeg(infile).audioCodec('copy')
    .outputOptions ...metadata-options
    .on 'start' -> console.log '[ffmpeg] start'
    .on 'end' -> console.log '[ffmpeg] end' ; resolve!
    .on 'error' (err, stdout, stderr) ->
      console.log '[ffmpeg] error: ' + err.message
      reject err
    .save(outfile)


find-input-files = (spec) ->
  if typeof spec == 'string'
    try
      l = fs.lstatSync spec
      if l.isDirectory!
        ext = MEDIA_EXTENSIONS.join('|')
        [path.join(spec, fn) for fn in fs.readdirSync(spec)
         when //[.](#{ext})$//.exec(fn)]
      else
        [spec]
    catch e
      console.error "#{spec}: #e"
      []
  else
    [].concat ...[find-input-files .. for spec]

get-root-dir = (spec) ->
  if typeof spec == 'string'
    try
      l = fs.lstatSync spec
      if l.isDirectory! then return spec
      else return path.dirname(spec)
    catch
      void
  else
    for d in spec
      if (res = get-root-dir d)? then return res


# OOPS
thunky = require 'thunky'
configure-ffmpeg = thunky (callback) ->
  p = new Promise (resolve, reject) ->
    err, path <- (new ffmpeg) ._getFfmpegPath
    if path then resolve!
    # Try to find ffmpeg using mdfind
    child_process = require 'child_process'
    for line in child_process.spawnSync('mdfind', ['-name', 'ffmpeg'], {encoding: 'utf-8'}).stdout.split("\n")
      if /[/]ffmpeg$/.exec(line) && fs.statSync(line).isFile()
        console.log "[ffmpeg] path: #{line}"
        ffmpeg.setFfmpegPath line
        resolve! ; return
    reject err ? new Error "ffmpeg not found"
  p.then callback


if typeof module != 'undefined' && require.main == module
  argv = require('minimist')(process.argv.slice(2))
  BUILTIN = <[_ o output-dir]>
  metadata = {}
    for k,v of argv then if k not in BUILTIN then ..[k] = v
  console.log metadata
  dir-path = argv._[0] ? '.'

  set-album-metadata (if argv._[0] then argv._ else '.'), metadata, argv.o ? argv['output-dir']
