local WIDTH = 260
local HEIGHT = 37

local asepriteOut = app.params["aseprite_out"]
local layersDir = app.params["layers_dir"]

if not asepriteOut or asepriteOut == "" then
  error("Missing --script-param aseprite_out=<path>")
end
if not layersDir or layersDir == "" then
  error("Missing --script-param layers_dir=<path>")
end

local sprite = Sprite(WIDTH, HEIGHT, ColorMode.RGB)
sprite.filename = asepriteOut

local orderedLayers = {
  "empty_bar",
  "health_fill",
  "frame",
  "decorations",
  "highlights",
}

for index, name in ipairs(orderedLayers) do
  local layer
  if index == 1 then
    layer = sprite.layers[1]
  else
    layer = sprite:newLayer()
  end
  layer.name = name

  local image = Image { fromFile = layersDir .. "/" .. name .. ".png" }
  if image.width ~= WIDTH or image.height ~= HEIGHT then
    error(name .. " layer has unexpected dimensions")
  end
  sprite:newCel(layer, 1, image, Point(0, 0))
end

sprite:saveAs(asepriteOut)
app.activeSprite = sprite
app.refresh()
