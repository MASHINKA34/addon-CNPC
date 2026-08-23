local LAYERS = {"timer_background", "timer_fill", "timer_frame"}
local STYLES = {
  moss_cave = {260, 10},
  ghost_dungeon = {1329, 54},
  infernal = {182, 7},
  sculk = {256, 12},
}

local function join(...)
  local parts = {...}
  local result = parts[1]
  for index = 2, #parts do
    result = app.fs.joinPath(result, parts[index])
  end
  return result
end

local function namedLayer(sprite, name)
  for _, layer in ipairs(sprite.layers) do
    if layer.name == name then return layer end
  end
  return nil
end

local function saveLayer(source, layer, outputPath)
  local cel = layer:cel(1)
  if not cel then error(layer.name .. " has no cel on frame 1") end
  local output = Sprite(source.width, source.height, ColorMode.RGB)
  local target = output.layers[1]:cel(1).image
  target:clear(app.pixelColor.rgba(0, 0, 0, 0))
  local image = cel.image
  for y = 0, image.height - 1 do
    for x = 0, image.width - 1 do
      target:drawPixel(cel.position.x + x, cel.position.y + y, image:getPixel(x, y))
    end
  end
  local saved = output:saveAs(outputPath)
  output:close()
  if not saved then error("Could not save " .. outputPath) end
end

local project = app.params["projectDir"] or app.fs.currentPath
local sourceRoot = join(project, "art", "aseprite", "boss_bar")
local outputRoot = join(
  project, "src", "main", "resources", "assets", "cnpcgeckoaddon",
  "textures", "gui", "boss_bar"
)

for styleId, dimensions in pairs(STYLES) do
  local sourcePath = join(sourceRoot, styleId, "timer.aseprite")
  local source = app.open(sourcePath)
  if not source then error("Could not open " .. sourcePath) end
  if source.width ~= dimensions[1] or source.height ~= dimensions[2] then
    source:close()
    error(sourcePath .. " has unexpected dimensions")
  end
  if #source.frames ~= 1 or source.colorMode ~= ColorMode.RGB then
    source:close()
    error(sourcePath .. " must contain one RGBA frame")
  end
  local outputDir = join(outputRoot, styleId)
  app.fs.makeDirectory(outputDir)
  for _, layerName in ipairs(LAYERS) do
    local layer = namedLayer(source, layerName)
    if not layer then
      source:close()
      error(sourcePath .. " is missing layer " .. layerName)
    end
    saveLayer(source, layer, join(outputDir, layerName .. ".png"))
  end
  source:close()
  print("exported " .. styleId .. " timer")
end
