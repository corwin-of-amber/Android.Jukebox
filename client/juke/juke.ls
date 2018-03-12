#BASE_URL = "http://localhost:2222"
BASE_URL = "http://10.0.0.2:2222"

COMPILATIONS =
  * "Reign Vol. 1 (Original Soundtrack)"
  ...

app = angular.module "app" []
  ..controller \Ctrl ($scope) ->
    $scope.tracks = []
    $scope.albums = []
    $scope.artists = []
    $scope.select = {}

    $scope.volume = 275
    $scope.max-volume = 500
    $scope.volume-enabled = true

    $scope.base-url = BASE_URL

    $scope.pred = (.['album'] not in COMPILATIONS)

    $scope.play = (ids) ->
      if $.isArray(ids) then ids = ids.join(",")
      data <-! $.post @base-url + "/play", ""+ids
      console.log "play: " + data
    $scope.stop = ->
      data <-! $.post @base-url + "/stop"
      console.log "stop: " + data

    $scope.cu = (d) ->
      {}
        for k,v of d
          if v? then ..[k] = v

    $scope.slice = (viewed, index) ->
      [t._id for t in viewed[index to]]

    $scope.reload-tracks = ->
      $.ajax do
        type: \GET
        contentType: "application/json;charset=utf-8"
        url: @base-url
        success: (data) !->
          <- $scope.$apply
          $scope.tracks = data

    $scope.reload-volume = ->
      $scope.volume-enabled = false
      $.get "#{$scope.base-url}/vol" .done (data) ->
        console.log "volume = #{data}"
        if data?
          $scope.$apply -> $scope.volume = $scope.max-volume * (+data)
      .always ->
        $scope.volume-enabled = true

    $scope.reload = ->
      app.juke-url = $scope.base-url
      $scope.reload-tracks!
      $scope.reload-volume!

    $scope.reload!

    $scope.start-upload = -> upload @base-url, @~reload-tracks

    $scope.$watch \volume (volume) ->
      if $scope.volume-enabled
        max = $scope.max-volume
        $.get "#{$scope.base-url}/vol?#volume/#max"

  ..filter \project ->
    (tracks, [column, pred, th]) -> project tracks, column, pred, th

  ..filter \tracknum ->
    (value) -> if value ~= 0 then "" else value

uniq = (values, threshold=1) -> []
  hist = {}
  for v in values
    if (hist[v] = (hist[v] ? 0) + 1) == threshold then ..push v

project = (tracks, column, pred=(->true), threshold) ->
  uniq [(if pred(..) then ..[column]) for tracks], threshold

upload = (base-url, reload-cb) ->
  fe = document.forms.upload.elements
  fd = new FormData
  $.each fe.upload.files, (i, file) ->
    fd.append 'payload', file
  qs = if fe.play.checked then "?play" else ""
  $.ajax do
    url: "#{base-url}/upload#qs"
    type: \POST
    data: fd
    processData: false
    contentType: false
    xhr: ->
      xhr = $.ajax-settings.xhr!
      if xhr.upload
        acc = 1
        xhr.upload.addEventListener \progress (e) ->
          pc = Math.round(100 * acc * e.loaded / e.total) / acc
          if pc < 1 then acc := 10
          $ \#percentage .text "#pc%"
      xhr
    success: (data) !->
      $ \#percentage .text ""
      reload-cb!
      console.log data
    error: (xhr, status, error) !->
      console.log "Upload failed."
      console.log "Reason: #status - #error"

$ ->
  $(document).on 'paste' ->
    text = it.originalEvent.clipboardData.getData("text/plain")
    if text?
      # strip google prefix
      if /^(https?:[/][/])?www[.]google[.]com[/]url/.exec(text)
        text = new URL(text).searchParams.get('url') ? text
      # see if text is a youtube url
      if /^(https?:[/][/])?(\w*[.])?youtu[.]?be/.exec(text)
        youtube-play(text, app.juke-url).catch -> console.error it


export app
